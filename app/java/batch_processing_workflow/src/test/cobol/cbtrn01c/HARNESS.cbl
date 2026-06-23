*> Oracle driver for CBTRN01C (verify daily transactions). File-I/O adaptation only:
*>  - converts the normalized daily-tran (LINE SEQUENTIAL) to the flat RECORD
*>    SEQUENTIAL file the program reads (DALYTRAN),
*>  - loads XREF and ACCOUNT into BDB INDEXED files (the program reads these),
*>  - creates empty CUSTFILE/CARDFILE/TRANFILE (program OPENs but never reads them),
*>  - CALLs the UNMODIFIED CBTRN01C, whose SYSOUT is the golden. Compile -free.
identification division.
program-id. HARNESS.

environment division.
input-output section.
file-control.
    select daly-src assign to SRCDALY organization line sequential file status fs.
    select daly-out assign to DALYTRAN organization sequential file status fs.
    select xr-src assign to SRCXREF organization line sequential file status fs.
    select ac-src assign to SRCACCT organization line sequential file status fs.
    select xr-file assign to XREFFILE organization indexed access dynamic record key xr-key file status fs.
    select ac-file assign to ACCTFILE organization indexed access dynamic record key ac-key file status fs.
    select cu-file assign to CUSTFILE organization indexed access dynamic record key cu-key file status fs.
    select cd-file assign to CARDFILE organization indexed access dynamic record key cd-key file status fs.
    select tr-file assign to TRANFILE organization indexed access dynamic record key tr-key file status fs.

data division.
file section.
fd daly-src. 01 daly-src-rec pic x(350).
fd daly-out. 01 daly-out-rec pic x(350).
fd xr-src.   01 xr-src-rec   pic x(50).
fd ac-src.   01 ac-src-rec   pic x(300).
fd xr-file.  01 xr-rec. 05 xr-key pic x(16). 05 filler pic x(34).
fd ac-file.  01 ac-rec. 05 ac-key pic 9(11). 05 filler pic x(289).
fd cu-file.  01 cu-rec. 05 cu-key pic 9(09). 05 filler pic x(491).
fd cd-file.  01 cd-rec. 05 cd-key pic x(16). 05 filler pic x(134).
fd tr-file.  01 tr-rec. 05 tr-key pic x(16). 05 filler pic x(334).

working-storage section.
01 fs     pic xx.
01 eof    pic x value 'n'.
01 ws-pgm pic x(12) value 'CBTRN01C'.

procedure division.
main-para.
    accept ws-pgm from environment 'PGMNAME' on exception continue end-accept

    perform convert-daly
    perform load-xref
    perform load-acct
    perform create-empty

    call ws-pgm
    goback.

convert-daly.
    open input daly-src
    open output daly-out
    move 'n' to eof
    perform until eof = 'y'
        read daly-src into daly-out-rec
            at end move 'y' to eof
            not at end write daly-out-rec
        end-read
    end-perform
    close daly-src daly-out.

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

create-empty.
    open output cu-file  close cu-file
    open output cd-file  close cd-file
    open output tr-file  close tr-file.
