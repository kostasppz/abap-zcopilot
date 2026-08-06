REPORT z_bad_hr_report.

* Canonical "bad" example: SELECT inside a loop, SELECT *, and personal
* data written to list/spool output.
*
* Expected findings:
*   PERF_SELECT_IN_LOOP
*   PERF_SELECT_STAR
*   PRIV_PERSONAL_DATA_IN_SPOOL
*   PRIV_BROAD_HR_MASTER_DATA_SELECTION

DATA lt_person TYPE STANDARD TABLE OF pernr_d.

LOOP AT lt_person INTO DATA(ls_person).
  SELECT SINGLE *
    FROM pa0002
    INTO @DATA(ls_pa0002)
    WHERE pernr = @ls_person-pernr.

  WRITE: / ls_pa0002-pernr,
           ls_pa0002-nachn,
           ls_pa0002-vorna.
ENDLOOP.
