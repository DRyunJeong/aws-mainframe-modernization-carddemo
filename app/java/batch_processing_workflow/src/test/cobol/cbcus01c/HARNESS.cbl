*> Oracle driver for CBCUS01C (read & print customer file). Loads normalized
*> custdata into a BDB INDEXED file keyed by customer id (the JCL's CUSTFILE KSDS)
*> and CALLs the UNMODIFIED CBCUS01C, whose SYSOUT is the golden. Compile -free.
identification division.
program-id. HARNESS.
environment division.
input-output section.
file-control.
    select cu-src assign to SRCCUST
        organization line sequential file status src-fs.
    select cu-file assign to CUSTFILE
        organization indexed access dynamic
        record key cu-key file status idx-fs.
data division.
file section.
fd cu-src. 01 cu-src-rec pic x(500).
fd cu-file.
01 cu-rec.
   05 cu-key pic 9(9).
   05 filler pic x(491).
working-storage section.
01 src-fs pic xx.
01 idx-fs pic xx.
01 eof    pic x value 'n'.
01 ws-pgm pic x(12) value 'CBCUS01C'.
procedure division.
main-para.
    accept ws-pgm from environment 'PGMNAME' on exception continue end-accept
    open output cu-file
    open input  cu-src
    perform until eof = 'y'
        read cu-src into cu-rec
            at end move 'y' to eof
            not at end write cu-rec
        end-read
    end-perform
    close cu-src cu-file
    call ws-pgm
    goback.
