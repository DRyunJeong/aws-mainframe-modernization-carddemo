       identification division.
       program-id. LDACCT.
       environment division.
       input-output section.
       file-control.
           select s assign to SRCACCT organization line sequential
               file status f1.
           select d assign to ACCTFILE organization indexed access dynamic
               record key k file status f2.
       data division.
       file section.
       fd s. 01 s-rec pic x(300).
       fd d. 01 d-rec. 05 k pic x(11). 05 filler pic x(289).
       working-storage section.
       01 f1 pic xx. 01 f2 pic xx. 01 eof pic x value 'n'.
       procedure division.
           open output d open input s
           perform until eof = 'y'
               read s at end move 'y' to eof
                   not at end move s-rec to d-rec write d-rec end-read
           end-perform
           close s d goback.
