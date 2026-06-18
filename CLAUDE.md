# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CardDemo is a mainframe credit card management application written primarily in COBOL, designed by AWS to exercise and showcase mainframe migration/modernization tooling (discovery, analysis, transformation, performance testing). The code intentionally mixes coding styles, data formats, and dataset types so that it stresses analysis tools the way a real-world legacy mainframe codebase would. There is no local runtime — the application is designed to run under CICS/VSAM/JCL on an actual mainframe (or a mainframe-emulation environment), not on a workstation.

## Repository Layout

- `app/` — base application source, organized by artifact type:
  - `cbl/` — COBOL programs (CICS online programs and batch programs, mixed together)
  - `cpy/` — copybooks shared across programs (data structures)
  - `bms/` + `cpy-bms/` — BMS screen map sources and their generated map copybooks
  - `jcl/` — batch job JCL (file loads, batch processing, utilities)
  - `proc/` — cataloged procedures used by JCL
  - `csd/` — CICS resource definitions (CSD) for installing transactions/programs/mapsets
  - `asm/` + `maclib/` — Assembler modules (MVSWAIT timer control, COBDATFT date conversion) and their macros
  - `data/EBCDIC/` and `data/ASCII/` — sample datasets for initial load, in mainframe and workstation encodings respectively
  - `catlg/` — VSAM catalog listing reference (`LISTCAT.txt`)
  - `scheduler/` — CA7 and Control-M job scheduler definitions
  - `app-authorization-ims-db2-mq/`, `app-transaction-type-db2/`, `app-vsam-mq/` — **optional extension modules**, each self-contained with its own `cbl/`, `cpy/`, `bms/`, `jcl/`, `csd/`, plus `dcl/`/`ddl/`/`ctl/` (DB2) or `ims/` (IMS DB) as needed. Each has its own README describing installation and DB2/IMS/MQ resource setup.
- `samples/jcl/` and `samples/proc/` — example compile JCL/procs (`BATCMP`, `BMSCMP`, `CICCMP`, `CICDBCMP`, `IMSMQCMP`, `BUILDONL.prc`, `BUILDBAT.prc`, `BUILDBMS.prc`, `BLDCIDB2.prc`) showing how programs are compiled and link-edited on the mainframe (IGYCRCTL compile step, DFHEILID/BMS translate step, HEWL link-edit step). Use these as templates, not literally — HLQs and library names must be adapted to the target environment.
- `samples/m2/` — packaged runtimes for AWS Mainframe Modernization (M2) and UniKix, for running CardDemo on those platforms instead of a physical mainframe.
- `scripts/` — local helper scripts for working against a real mainframe over an FTP tunnel (see below).
- `diagrams/` — architecture/flow diagrams referenced from the READMEs (`Application-Flow-User.png`, `Application-Flow-Admin.png`, `auth_flow.png`, etc).

## Working with the Code

There is no local build, test, or lint tooling in this repo (no Makefile, CI config, or unit test framework) — "building" means compiling COBOL/BMS/Assembler sources on a real mainframe, CICS region, or M2/UniKix runtime.

### Local syntax checking
GnuCOBOL (`cobc`) can be used locally only as a rough syntax sanity check, not as a real build:
```
cobc -I app/cpy/ -fsyntax-only --std=ibm-strict app/cbl/<PROGRAM>.cbl
```
This will not catch CICS/VSAM/DB2/IMS-specific issues since those EXEC CICS/EXEC SQL/DL/I calls aren't resolvable outside a mainframe environment.

### scripts/ — remote mainframe workflow
These scripts assume an FTP tunnel to a remote mainframe (Ensono) is already running on `localhost:2121`; each script checks for that tunnel and aborts with "FTP Tunnel to Ensono not running." if it isn't found. They are not usable without that tunnel and corresponding mainframe access:
- `upld_module.sh <path> <module_type>` — pads a source file to 80-byte mainframe record length (`pad.awk`) and FTPs it into the corresponding PDS member (e.g. `AWS.M2.CARDDEMO.CBL(member)`).
- `remote_compile.sh <file> <ext> <basename>` — only accepts `.cbl` files; substitutes the member name into `compile_batch.jcl.template` and submits the resulting JCL via FTP (`filetype=JES`).
- `remote_submit.sh <file.jcl>` — submits an arbitrary JCL file via FTP.
- `remote_refresh.sh` — submits the full sequence of data-refresh JCLs (CLOSEFIL → ACCTFILE/CARDFILE/XREFFILE/CUSTFILE/TRANFILE/DISCGRP/TCATBALF/TRANCATG/TRANTYPE/DUSRSECJ → OPENFIL).
- `run_full_batch.sh` — submits the full batch cycle (data refresh, then POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL), mirroring the "Running Batch Jobs" sequence in the root README.
- `run_posting.sh`, `run_interest_calc.sh` — submit individual stages of the batch cycle.
- `git-addSrcVersionInfo.sh <file>` — stamps a source file with a version comment derived from `git describe`/commit count (comment syntax chosen by extension: `cbl/cob/cpy` → `      *`, `jcl/prc/proc` → `//*`, `bms` → `*`, `py` → `##`). Run before uploading a module if version stamping is desired; it mutates the file tags in the local git repo as a side effect (deletes all tags except the configured `app_version`), so be cautious running it in an automated context.

Because all of these scripts require a live FTP tunnel and real mainframe datasets, none of them can be exercised in this sandbox — treat changes to them as code review/static analysis rather than something to "run and verify."

## Architecture Notes

### Program types and naming
- **CICS online programs**: prefixed `CO*` (e.g. `COSGN00C` signon, `COMEN01C` main menu, `COACTVWC`/`COACTUPC` account view/update, `COCRDLIC`/`COCRDSLC`/`COCRDUPC` card list/view/update, `COTRN00C`/`COTRN01C`/`COTRN02C` transaction list/view/add, `COBIL00C` bill pay, `COADM01C` admin menu, `COUSR0*C` user management). Each maps 1:1 to a CICS transaction ID and a BMS mapset (see the Application Inventory table in the root README for the full transaction/program/mapset matrix).
- **Batch programs**: prefixed `CB*` (e.g. `CBACT01C`–`CBACT04C` account file processing, `CBCUS01C` customer, `CBTRN01C`–`CBTRN03C` transaction processing/reporting, `CBSTM03A`/`CBSTM03B` statement generation, `CBEXPORT`/`CBIMPORT` data export/import). Batch programs read/write flat VSAM/sequential files declared in `FILE-CONTROL`, not via CICS.
- A CICS program follows: copy in common copybooks (`COCOM01Y` commarea, screen copybook from `cpy-bms/`, title/date/message copybooks, `DFHAID`/`DFHBMSCA`), receive `DFHCOMMAREA` via `LINKAGE SECTION`, and dispatch from `MAIN-PARA` based on `EIBCALEN`/pseudo-conversational state.
- Copybooks in `app/cpy/` are the canonical record layouts for VSAM files (e.g. `CVACT01Y` account, `CVACT02Y` card, `CVCUS01Y` customer, `CVACT03Y` card/account/customer cross-reference, `CVTRA0*Y` transaction/category/disclosure-group records, `CSUSR01Y` user security). These same layouts are shared between online (CICS) and batch programs and correspond directly to the dataset table in the root README's Installation section.

### Optional modules are additive, not edits to base code
The three `app/app-*` extensions (DB2 transaction-type management, IMS/DB2/MQ authorizations, VSAM/MQ account extraction) layer new transactions/programs/copybooks on top of the base app without modifying base `cbl/`/`cpy/` files (aside from enabling Admin Menu options). When asked to extend or modify one of these optional features, keep changes scoped to that module's own subdirectory and follow the existing DCL/DDL/IMS conventions documented in its README rather than introducing a new pattern.

### Data flow
Online (CICS) and batch programs both operate against the same underlying VSAM KSDS files (account, card, customer, cross-reference, transaction) — there is no separate database for the base app. DB2 and IMS DB are only introduced by the optional modules (transaction-type reference data in DB2; authorization records in IMS HIDAM + fraud tracking in DB2). The batch cycle in the root README's "Running Batch Jobs" table (and reproduced in `scripts/run_full_batch.sh`) is the authoritative sequence for how nightly processing flows: refresh master files → post transactions → calculate interest → backup → combine → rebuild alternate index → reopen files for CICS.
