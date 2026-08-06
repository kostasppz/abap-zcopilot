REPORT z_good_hr_report.

* Corrected version of samples/bad/z_bad_hr_report.abap:
*  - one mass SELECT with an explicit, minimal field list instead of a
*    SELECT SINGLE * per loop iteration,
*  - FOR ALL ENTRIES guarded by an emptiness check,
*  - no personal data is written to the spool at all; only an aggregate
*    count leaves the program.

DATA lt_person TYPE STANDARD TABLE OF pernr_d.
DATA lt_pa0002 TYPE STANDARD TABLE OF ty_pa0002_key.

IF lt_person IS NOT INITIAL.
  SELECT pernr
    FROM pa0002
    INTO TABLE @lt_pa0002
    FOR ALL ENTRIES IN @lt_person
    WHERE pernr = @lt_person-table_line.
ENDIF.

" Personal data stays in memory; only an aggregate count is output.
DATA(lv_count) = lines( lt_pa0002 ).
WRITE: / 'Processed employee records:', lv_count.
