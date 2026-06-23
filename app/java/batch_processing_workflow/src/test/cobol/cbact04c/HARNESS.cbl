*> ============================================================
*> HARNESS - GnuCOBOL oracle driver for CBACT04C (interest calc).
*> File-I/O adaptation ONLY (no business logic). It:
*>   1. loads the 4 KSDS inputs from normalized LINE SEQUENTIAL
*>      test data into BDB INDEXED files (same keys as the mainframe
*>      VSAM defs, incl. XREF alternate key on account-id),
*>   2. CALLs the UNMODIFIED CBACT04C with the JCL-style PARM date,
*>   3. dumps ACCTFILE back to a flat file in key order so the
*>      golden master can be compared byte-for-byte.
*> All file names are wired through environment variables so that
*> app/cbl/CBACT04C.cbl is compiled and run with ZERO source edits.
*> Compile with -free.  Records are passed through whole (key+rest)
*> so every byte of the original test data is preserved.
*> ============================================================
identification division.
program-id. HARNESS.

environment division.
input-output section.
file-control.
*> ---- normalized text inputs (LINE SEQUENTIAL) ----
    select tc-src assign to SRCTCAT
        organization line sequential file status src-fs.
    select xr-src assign to SRCXREF
        organization line sequential file status src-fs.
    select dg-src assign to SRCDISC
        organization line sequential file status src-fs.
    select ac-src assign to SRCACCT
        organization line sequential file status src-fs.
*> ---- the 4 KSDS the program reads (same DD names as JCL) ----
    select tc-file assign to TCATBALF
        organization indexed access dynamic
        record key tc-key file status idx-fs.
    select xr-file assign to XREFFILE
        organization indexed access dynamic
        record key xr-card
        alternate record key xr-acct
        file status idx-fs.
    select dg-file assign to DISCGRP
        organization indexed access dynamic
        record key dg-key file status idx-fs.
    select ac-file assign to ACCTFILE
        organization indexed access dynamic
        record key ac-key file status idx-fs.
*> ---- ACCTFILE dump (fixed RECORD SEQUENTIAL, key order, flat 300-byte
*>      records, no newline/trim - byte-for-byte comparable like TRANSACT) ----
    select ac-dump assign to ACCTDUMP
        organization sequential file status dmp-fs.

data division.
file section.
fd tc-src. 01 tc-src-rec pic x(50).
fd xr-src. 01 xr-src-rec pic x(50).
fd dg-src. 01 dg-src-rec pic x(50).
fd ac-src. 01 ac-src-rec pic x(300).

fd tc-file.
01 tc-rec.
   05 tc-key   pic x(17).
   05 filler   pic x(33).
fd xr-file.
01 xr-rec.
   05 xr-card  pic x(16).
   05 xr-cust  pic x(09).
   05 xr-acct  pic x(11).
   05 filler   pic x(14).
fd dg-file.
01 dg-rec.
   05 dg-key   pic x(16).
   05 filler   pic x(34).
fd ac-file.
01 ac-rec.
   05 ac-key   pic x(11).
   05 filler   pic x(289).
fd ac-dump.
01 ac-dump-rec pic x(300).

working-storage section.
01 src-fs   pic xx.
01 idx-fs   pic xx.
01 dmp-fs   pic xx.
01 eof      pic x value 'n'.
01 ws-parms.
   05 ws-parm-len  pic s9(4) comp value 10.
   05 ws-parm-date pic x(10) value '2022071800'.
01 ws-pgm        pic x(12) value 'CBACT04C'.

procedure division.
main-para.
    accept ws-parm-date from environment 'PARMDATE'
        on exception continue
    end-accept
    accept ws-pgm from environment 'PGMNAME'
        on exception continue
    end-accept

    perform load-tcatbal
    perform load-xref
    perform load-discgrp
    perform load-acct

    call ws-pgm using ws-parms

    perform dump-acct
    goback.

*> ---------------------------------------------------------
load-tcatbal.
    open output tc-file
    open input  tc-src
    move 'n' to eof
    perform until eof = 'y'
        read tc-src into tc-rec
            at end move 'y' to eof
            not at end write tc-rec
        end-read
    end-perform
    close tc-src tc-file.

load-xref.
    open output xr-file
    open input  xr-src
    move 'n' to eof
    perform until eof = 'y'
        read xr-src into xr-rec
            at end move 'y' to eof
            not at end write xr-rec
        end-read
    end-perform
    close xr-src xr-file.

load-discgrp.
    open output dg-file
    open input  dg-src
    move 'n' to eof
    perform until eof = 'y'
        read dg-src into dg-rec
            at end move 'y' to eof
            not at end write dg-rec
        end-read
    end-perform
    close dg-src dg-file.

load-acct.
    open output ac-file
    open input  ac-src
    move 'n' to eof
    perform until eof = 'y'
        read ac-src into ac-rec
            at end move 'y' to eof
            not at end write ac-rec
        end-read
    end-perform
    close ac-src ac-file.

*> ---------------------------------------------------------
dump-acct.
    open input  ac-file
    open output ac-dump
    move 'n' to eof
    perform until eof = 'y'
        read ac-file next record into ac-rec
            at end move 'y' to eof
            not at end
                move ac-rec to ac-dump-rec
                write ac-dump-rec
        end-read
    end-perform
    close ac-file ac-dump.
