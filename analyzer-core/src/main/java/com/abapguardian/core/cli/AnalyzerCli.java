package com.abapguardian.core.cli;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.engine.AnalysisResult;
import com.abapguardian.core.engine.RuleEngine;
import com.abapguardian.core.json.JsonSerializer;
import com.abapguardian.core.rules.RuleRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal command line interface:
 *
 * <pre>
 *   java -jar analyzer-core.jar &lt;file.abap&gt; [config.yaml]
 *   cat code.abap | java -jar analyzer-core.jar -
 * </pre>
 *
 * Prints the analysis result as JSON to stdout. Never prints the source code.
 */
public final class AnalyzerCli {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: analyzer-core <file.abap|-> [config.yaml]");
            System.exit(2);
        }
        String source;
        if ("-".equals(args[0])) {
            source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        } else {
            source = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        }
        RuleConfiguration config = RuleConfiguration.defaults();
        if (args.length > 1) {
            try (InputStream in = Files.newInputStream(Path.of(args[1]))) {
                config = RuleConfiguration.fromYaml(in);
            }
        }
        RuleEngine engine = new RuleEngine(RuleRegistry.allRules(), config);
        String objectName = "-".equals(args[0]) ? "STDIN" : Path.of(args[0]).getFileName().toString();
        AnalysisResult result = engine.analyze(source, objectName, "PROG");
        System.out.println(new JsonSerializer().toJson(result));
    }

    private AnalyzerCli() {
    }
}
