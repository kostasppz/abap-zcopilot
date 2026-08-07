package com.abapguardian.eclipse.views;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.adapter.AdtEditorAdapter;
import com.abapguardian.eclipse.api.GuardianChatMessage;
import com.abapguardian.eclipse.api.GuardianChatResponse;
import com.abapguardian.eclipse.handlers.AnalyzeCurrentHandler;
import com.abapguardian.eclipse.service.GatewayClient;
import com.abapguardian.eclipse.ui.GuardianUiState;
import com.abapguardian.eclipse.ui.SuggestedFixDialog;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dockable ABAP Copilot chat with active-editor and selection context. */
public class CopilotView extends ViewPart {

    public static final String ID = "com.abapguardian.eclipse.views.copilot";

    private final List<GuardianChatMessage> history = new ArrayList<>();
    private StyledText transcript;
    private Text prompt;
    private Button askButton;
    private Button includeContext;
    private Button reviewButton;
    private Label status;
    private Consumer<GuardianUiState.State> stateListener;
    private IEditorPart lastSuggestionEditor;
    private String lastOriginalSelection = "";
    private String lastSuggestedCode = "";
    private boolean expectCorrection;

    private static final Pattern CODE_FENCE = Pattern.compile(
            "```(?:abap)?\\s*(.*?)```", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Composite statusRow = new Composite(parent, SWT.NONE);
        statusRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusRow.setLayout(new GridLayout(2, false));
        status = new Label(statusRow, SWT.NONE);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        includeContext = new Button(statusRow, SWT.CHECK);
        includeContext.setText("Use active ABAP editor/selection as context");
        includeContext.setSelection(true);

        transcript = new StyledText(parent,
                SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        transcript.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        transcript.setText(
                "ABAP Guardian Copilot\n\n"
                + "Ask about the active ABAP object, a selected code block, security, "
                + "performance, privacy, or the bundled project rules.\n\n"
                + "Suggested code always requires human review.\n");

        prompt = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData promptData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        promptData.heightHint = 70;
        prompt.setLayoutData(promptData);
        prompt.setMessage("Ask ABAP Guardian… (Ctrl+Enter to send)");
        prompt.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.keyCode == SWT.CR && (event.stateMask & SWT.CTRL) != 0) {
                    sendQuestion();
                    event.doit = false;
                }
            }
        });

        Composite actions = new Composite(parent, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        actions.setLayout(new GridLayout(6, false));
        askButton = button(actions, "Ask", event -> sendQuestion());
        button(actions, "Analyze editor", event -> analyzeEditor());
        button(actions, "Explain selection", event -> sendPreset(
                "Explain the selected ABAP code, including its behavior and risks."));
        button(actions, "Suggest correction", event -> sendPreset(
                "Suggest a safer or faster alternative for the selected ABAP code. "
                + "Explain behavior changes and return code that must be reviewed."));
        reviewButton = button(actions, "Review last suggestion…",
                event -> reviewLastSuggestion());
        reviewButton.setEnabled(false);
        button(actions, "Clear chat", event -> clearChat());

        stateListener = state -> Display.getDefault().asyncExec(() -> {
            if (status != null && !status.isDisposed()) {
                status.setText("● " + state.message());
                status.getParent().layout();
            }
        });
        GuardianUiState.addListener(stateListener);
    }

    private Button button(Composite parent, String text,
                          org.eclipse.swt.widgets.Listener listener) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, listener);
        return button;
    }

    public void setPrompt(String text, boolean sendImmediately) {
        if (prompt == null || prompt.isDisposed()) {
            return;
        }
        prompt.setText(text == null ? "" : text);
        prompt.setFocus();
        if (sendImmediately) {
            sendQuestion();
        }
    }

    private void sendPreset(String question) {
        IEditorPart editor = activeEditor();
        if (editor == null || AdtEditorAdapter.getSelectedText(editor).isBlank()) {
            append("Guardian", "Select ABAP code in the active editor first.");
            return;
        }
        expectCorrection = question.startsWith("Suggest a safer");
        setPrompt(question, true);
    }

    private void sendQuestion() {
        String question = prompt.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        IEditorPart editor = activeEditor();
        boolean withContext = includeContext.getSelection() && editor != null;
        String source = withContext ? AdtEditorAdapter.getSource(editor).orElse("") : "";
        String selection = withContext ? AdtEditorAdapter.getSelectedText(editor) : "";
        String objectName = editor == null ? "UNKNOWN" : AdtEditorAdapter.getObjectName(editor);
        String objectType = editor == null ? "PROG" : AdtEditorAdapter.getObjectType(editor);
        List<GuardianChatMessage> prior = List.copyOf(history);
        boolean correctionRequest = expectCorrection
                || (question.toLowerCase().contains("suggest") && !selection.isBlank());
        expectCorrection = false;

        addTurn(new GuardianChatMessage("user", question));
        append("You", question);
        prompt.setText("");
        askButton.setEnabled(false);
        GuardianUiState.set(GuardianUiState.Kind.CHATTING, "Guardian is thinking…");

        Job job = new Job("ABAP Guardian Copilot") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    GuardianChatResponse response = new GatewayClient().chat(
                            question, source, selection, objectName, objectType, prior);
                    Display.getDefault().asyncExec(() -> {
                        if (transcript == null || transcript.isDisposed()) {
                            return;
                        }
                        addTurn(new GuardianChatMessage("assistant", response.answer()));
                        String references = response.knowledgeReferences().isEmpty()
                                ? "" : "\n\nKnowledge: "
                                + String.join(", ", response.knowledgeReferences());
                        append("Guardian", response.answer() + references);
                        if (correctionRequest) {
                            String code = extractCode(response.answer());
                            if (!code.isBlank()) {
                                lastSuggestionEditor = editor;
                                lastOriginalSelection = selection;
                                lastSuggestedCode = code;
                                reviewButton.setEnabled(true);
                            }
                        }
                        askButton.setEnabled(true);
                        GuardianUiState.set(GuardianUiState.Kind.SUCCESS,
                                "Connected · " + response.model());
                    });
                    return Status.OK_STATUS;
                } catch (GatewayClient.GatewayException exception) {
                    Activator.logError("Copilot request failed", exception);
                    Display.getDefault().asyncExec(() -> {
                        if (transcript == null || transcript.isDisposed()
                                || askButton == null || askButton.isDisposed()) {
                            return;
                        }
                        append("Guardian", "Request failed: " + exception.getMessage());
                        askButton.setEnabled(true);
                        GuardianUiState.set(GuardianUiState.Kind.ERROR,
                                "Copilot request failed");
                    });
                    return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
                            exception.getMessage(), exception);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    private void analyzeEditor() {
        IEditorPart editor = activeEditor();
        if (editor == null) {
            append("Guardian", "Open an ABAP editor first.");
            return;
        }
        AnalyzeCurrentHandler.analyze(getSite().getWorkbenchWindow(), editor);
    }

    private IEditorPart activeEditor() {
        IWorkbenchPage page = getSite().getWorkbenchWindow().getActivePage();
        return page == null ? null : page.getActiveEditor();
    }

    private void addTurn(GuardianChatMessage message) {
        history.add(message);
        while (history.size() > 12) {
            history.remove(0);
        }
    }

    private void append(String speaker, String text) {
        if (transcript == null || transcript.isDisposed()) {
            return;
        }
        transcript.append("\n" + speaker + "\n" + text.trim() + "\n");
        transcript.setTopIndex(transcript.getLineCount() - 1);
    }

    private void clearChat() {
        history.clear();
        lastSuggestionEditor = null;
        lastOriginalSelection = "";
        lastSuggestedCode = "";
        reviewButton.setEnabled(false);
        transcript.setText("ABAP Guardian Copilot\n\nConversation cleared.\n");
    }

    private void reviewLastSuggestion() {
        if (lastSuggestionEditor == null || lastSuggestedCode.isBlank()) {
            return;
        }
        SuggestedFixDialog.proposeSelectionFix(getSite().getShell(), lastSuggestionEditor,
                lastOriginalSelection, lastSuggestedCode);
    }

    private static String extractCode(String answer) {
        Matcher matcher = CODE_FENCE.matcher(answer == null ? "" : answer);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    public static CopilotView open(IWorkbenchWindow window) throws PartInitException {
        return (CopilotView) window.getActivePage().showView(ID);
    }

    public static void openWithPrompt(IWorkbenchWindow window, String text,
                                      boolean sendImmediately) throws PartInitException {
        open(window).setPrompt(text, sendImmediately);
    }

    @Override
    public void setFocus() {
        if (prompt != null) {
            prompt.setFocus();
        }
    }

    @Override
    public void dispose() {
        if (stateListener != null) {
            GuardianUiState.removeListener(stateListener);
        }
        super.dispose();
    }
}
