       identification division.
       program-id. DPTCAT.
       environment division.
       input-output section.
       file-control.
           select d assign to TCATBALF organization indexed access dynamic record key k file status f1.
           select o assign to TCATDUMP organization sequential file status f2.
       data division.
       file section.
       fd d. 01 d-rec. 05 k pic x(17). 05 filler pic x(33).
       fd o. 01 o-rec pic x(50).
       working-storage section.
       01 f1 pic xx. 01 f2 pic xx. 01 eof pic x value 'n'.
       procedure division.
           open input d open output o
           perform until eof = 'y'
               read d next record at end move 'y' to eof
                   not at end move d-rec to o-rec write o-rec end-read
           end-perform
           close d o goback.
