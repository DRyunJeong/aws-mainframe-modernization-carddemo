      ******************************************************************
      * COBDATFT - COBOL stand-in for the assembler date-format module
      * called by CBACT01C. The original is z/OS Assembler (app/asm) and
      * cannot run under GnuCOBOL, so for the local oracle it is replaced
      * with this faithful reimplementation of the ONE conversion CBACT01C
      * uses: CODATECN type '2' in (YYYY-MM-DD) -> outtype '2' (YYYYMMDD).
      * The Java migration applies the identical transform. (Documented
      * deviation: this date field's "oracle" is this stub, not the real
      * assembler.)
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. COBDATFT.
       DATA DIVISION.
       LINKAGE SECTION.
       COPY CODATECN.
       PROCEDURE DIVISION USING CODATECN-REC.
           MOVE SPACES TO CODATECN-0UT-DATE
           MOVE CODATECN-INP-DATE(1:4) TO CODATECN-0UT-DATE(1:4)
           MOVE CODATECN-INP-DATE(6:2) TO CODATECN-0UT-DATE(5:2)
           MOVE CODATECN-INP-DATE(9:2) TO CODATECN-0UT-DATE(7:2)
           GOBACK.
