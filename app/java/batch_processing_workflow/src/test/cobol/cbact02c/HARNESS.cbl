*> Oracle driver for CBACT02C (read & print card file). Loads normalized carddata
*> into a BDB INDEXED file keyed by card number (the JCL's CARDFILE KSDS) and CALLs
*> the UNMODIFIED CBACT02C, whose SYSOUT is the golden. Compile with -free.
identification division.
program-id. HARNESS.
environment division.
input-output section.
file-control.
    select cd-src assign to SRCCARD
        organization line sequential file status src-fs.
    select cd-file assign to CARDFILE
        organization indexed access dynamic
        record key cd-key file status idx-fs.
data division.
file section.
fd cd-src. 01 cd-src-rec pic x(150).
fd cd-file.
01 cd-rec.
   05 cd-key pic x(16).
   05 filler pic x(134).
working-storage section.
01 src-fs pic xx.
01 idx-fs pic xx.
01 eof    pic x value 'n'.
01 ws-pgm pic x(12) value 'CBACT02C'.
procedure division.
main-para.
    accept ws-pgm from environment 'PGMNAME' on exception continue end-accept
    open output cd-file
    open input  cd-src
    perform until eof = 'y'
        read cd-src into cd-rec
            at end move 'y' to eof
            not at end write cd-rec
        end-read
    end-perform
    close cd-src cd-file
    call ws-pgm
    goback.
