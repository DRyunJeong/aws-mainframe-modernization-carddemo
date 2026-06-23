*> Oracle driver for CBACT03C (read & print card xref). File-I/O adaptation only:
*> loads the normalized cardxref into a BDB INDEXED file keyed by card number
*> (as the JCL's XREFFILE KSDS) and CALLs the UNMODIFIED CBACT03C, whose SYSOUT
*> (DISPLAY) is the captured golden. Compile with -free.
identification division.
program-id. HARNESS.

environment division.
input-output section.
file-control.
    select xr-src assign to SRCXREF
        organization line sequential file status src-fs.
    select xr-file assign to XREFFILE
        organization indexed access dynamic
        record key xr-card file status idx-fs.

data division.
file section.
fd xr-src. 01 xr-src-rec pic x(50).
fd xr-file.
01 xr-rec.
   05 xr-card pic x(16).
   05 filler  pic x(34).

working-storage section.
01 src-fs pic xx.
01 idx-fs pic xx.
01 eof    pic x value 'n'.
01 ws-pgm pic x(12) value 'CBACT03C'.

procedure division.
main-para.
    accept ws-pgm from environment 'PGMNAME'
        on exception continue
    end-accept

    open output xr-file
    open input  xr-src
    perform until eof = 'y'
        read xr-src into xr-rec
            at end move 'y' to eof
            not at end write xr-rec
        end-read
    end-perform
    close xr-src xr-file

    call ws-pgm

    goback.
