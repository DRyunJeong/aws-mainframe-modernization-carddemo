       identification division.
       program-id. LDXREF.
       environment division.
       input-output section.
       file-control.
           select s assign to SRCXREF organization line sequential file status f1.
           select d assign to CARDXREF organization indexed access dynamic record key k file status f2.
       data division.
       file section.
       fd s. 01 s-rec pic x(50).
       fd d. 01 d-rec. 05 k pic x(16). 05 filler pic x(34).
       working-storage section.
       01 f1 pic xx. 01 f2 pic xx. 01 eof pic x value 'n'.
       procedure division.
           open output d open input s
           perform until eof = 'y'
               read s at end move 'y' to eof
                   not at end move s-rec to d-rec write d-rec end-read
           end-perform
           close s d goback.
