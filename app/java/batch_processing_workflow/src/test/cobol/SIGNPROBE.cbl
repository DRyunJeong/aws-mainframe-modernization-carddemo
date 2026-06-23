*> Probe: reveal GnuCOBOL's zoned-decimal (DISPLAY S9..V99) sign encoding
*> on OUTPUT and how it DECODES the EBCDIC-overpunch sample data on INPUT.
*> Compile: cobc -std=ibm -free -x SIGNPROBE.cbl -o signprobe
identification division.
program-id. SIGNPROBE.
data division.
working-storage section.
01 n        pic s9(10)v99.
01 nx redefines n pic x(12).
01 inx      pic x(12).
01 inn redefines inx pic s9(10)v99.
01 sep      pic s9(10)v99 sign leading separate.
01 sepx redefines sep pic x(13).

procedure division.
*> ---- OUTPUT encoding: known values -> raw 12 bytes ----
    move +0      to n  display 'OUT +0      =[' nx ']'
    move -0      to n  display 'OUT -0      =[' nx ']'
    move +1      to n  display 'OUT +1      =[' nx ']'
    move -1      to n  display 'OUT -1      =[' nx ']'
    move +194.00 to n  display 'OUT +194.00 =[' nx ']'
    move -194.00 to n  display 'OUT -194.00 =[' nx ']'
    move +0.05   to n  display 'OUT +0.05   =[' nx ']'
    move -0.05   to n  display 'OUT -0.05   =[' nx ']'
    move +123.45 to n  display 'OUT +123.45 =[' nx ']'
    move -123.45 to n  display 'OUT -123.45 =[' nx ']'

*> ---- INPUT decoding: feed sample-style bytes, show value via sign-separate ----
    move '00000001940{' to inx  move inn to sep
        display 'IN  00000001940{ ->[' sepx ']'
    move '0000000000{'  to inx(2:11)  move space to inx(1:1)
        move inn to sep  display 'IN  *0000000000{ ->[' sepx ']'
    move '000000019400' to inx  move inn to sep
        display 'IN  000000019400 ->[' sepx ']'
    move '00000001940A' to inx  move inn to sep
        display 'IN  00000001940A ->[' sepx ']'
    move '00000001940J' to inx  move inn to sep
        display 'IN  00000001940J ->[' sepx ']'
    move '0000000194p}' to inx  move inn to sep
        display 'IN  0000000194p} ->[' sepx ']'
    goback.
