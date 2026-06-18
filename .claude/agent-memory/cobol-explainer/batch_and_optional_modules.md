---
name: batch-and-optional-modules
description: Batch program I/O skeleton (CBTRN02C posting), the nightly batch sequence, and how the 3 optional modules (DB2/IMS/MQ) bolt onto the base app
metadata:
  type: project
---

배치 프로그램(`CB*`)은 CICS를 거치지 않고 `FILE-CONTROL`의 SELECT...ASSIGN으로 VSAM/순차 파일을 직접 연다. JCL의 DD명이 ASSIGN 명과 연결됨(예: POSTTRAN.jcl의 //DALYTRAN DD ... ↔ ASSIGN TO DALYTRAN). 표준 골격: 0000~0500 OPEN 단락들 → `PERFORM UNTIL END-OF-FILE='Y'`로 GET-NEXT 루프 → 9000~ CLOSE 단락들 → GOBACK. 각 I/O 후 FILE STATUS 2바이트를 검사해 정상('00')/EOF('10')/오류 분기. 오류 시 9999-ABEND-PROGRAM이 `CALL 'CEE3ABD'`로 강제 abend. reject 발생 시 RETURN-CODE 4 설정.

대표 배치 = CBTRN02C(거래 입력, job POSTTRAN). 6개 파일 사용: DALYTRAN(입력 순차), TRANSACT(출력 KSDS), CARDXREF(랜덤), DALYREJS(거절 출력 순차), ACCTDATA(I-O 랜덤), TCATBALF(I-O 랜덤). 각 일일거래마다: 1500-VALIDATE-TRAN(XREF로 카드→계정 조회, 신용한도/만료일 검증) → 통과 시 2000-POST-TRANSACTION(카테고리 잔액 갱신·없으면 생성, 계정 잔액/사이클 차변·대변 갱신, TRANSACT에 기록) / 실패 시 DALYREJS에 거절코드와 함께 기록.

야간 배치 시퀀스(root README "Running Batch Jobs" = scripts/run_full_batch.sh): 마스터 갱신(ACCTFILE/CARDFILE/XREFFILE/CUSTFILE...) → POSTTRAN(CBTRN02C) → INTCALC(CBACT04C 이자계산) → TRANBKP → COMBTRAN(SORT) → TRANIDX(AIX 정의) → OPENFIL(CICS에 파일 재오픈). CLOSEFIL/OPENFIL은 CICS가 잡고 있는 VSAM을 배치 전후로 닫고/여는 잡인데 **IEFBR14가 아니라 PGM=SDSF**로 ISFIN DD에 CEMT/`/F` 오퍼레이터 명령을 인라인 전달해 CICS 리전에 파일 CLOSE/OPEN을 지시한다(docs/jcl/CLOSEFIL.md·OPENFIL.md 확인. 일부 마스터 적재 잡 CARDFILE/CUSTFILE/TRANFILE도 자체 SDSF 스텝으로 CICS 닫기/열기를 내장).

배치 흐름 분석 시 비자명 함정 2가지(99개 docs 수확 시 확인): (a) **run_full_batch.sh는 TRANBKP.jcl을 두 번 호출** — 리프레시 단계의 "Refresh Transaction Data"와 사이클 후반 "Backup transaction Data" 양쪽에서 동일 멤버를 put한다(거래 마스터 초기화용 + 백업용 이중 용도). 시퀀스에 TRANBKP가 두 번 등장하는 게 오타가 아님. (b) **DEFGDGD는 H1/문서명이 "DB2 참조데이터 GDG"라 오해 소지** 있으나 실제 본문은 TRANTYPE/TRANCATG/DISCGRP 참조데이터 GDG 베이스 정의+IEBGENER 최초세대 적재다(DB2 전용 아님; 베이스 앱 VSAM 참조데이터). GDG 베이스 정의 잡은 DEFGDGB(거래 백업/일별/통합·SYSTRAN·TCATBALF.BKUP·TRANREPT)와 DEFGDGD(참조데이터) 둘로 나뉨 — 사이클 전 1회성 선행.

종합 산출물 위치: docs/catalog.txt(99항목 ID↔유형↔이름↔경로), docs/relationships.txt(6섹션: COPY/CALL·XCTL/파일I-O/JCL→PGM/선후행체인/카피북역참조), docs/batch_flow.md(mermaid 야간 사이클). 향후 의존성 질문은 이 3개를 먼저 참조.

== 이자 계산 배치 CBACT04C (job INTCALC, app/jcl/INTCALC.jcl STEP15) ==
PARM='YYYYMMDD0'(10바이트)로 처리일자를 받아 LINKAGE의 EXTERNAL-PARMS(PARM-LENGTH S9(4)COMP + PARM-DATE X(10))로 수신 — JCL PARM→COBOL 진입 표준 패턴. 입력 5파일: TCATBALF(거래카테고리 잔액 KSDS, ACCESS SEQUENTIAL=드라이빙 파일), XREFFILE(카드↔계정, AIX로 ACCT-ID 조회), ACCTFILE(I-O, 잔액 갱신), DISCGRP(디스클로저 그룹=이자율 마스터), TRANSACT(OUTPUT 순차=생성 이자거래). 흐름: TCATBALF를 계정ID 오름차순으로 순차 스캔하며 control-break 처리 — TRANCAT-ACCT-ID가 바뀌면(WS-LAST-ACCT-NUM 비교) 직전 계정에 대해 1050-UPDATE-ACCOUNT(누적이자 WS-TOTAL-INT를 ACCT-CURR-BAL에 더하고 사이클 차/대변 0으로 리셋 후 REWRITE)를 먼저 수행하고, 새 계정의 ACCT/XREF를 읽어둠. 각 카테고리行마다 DISCGRP에서 이자율 조회(없으면 status '23'→그룹ID 'DEFAULT'로 재조회=fallback). 핵심 산술(1300-COMPUTE-INTEREST): `COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200` — 월이자 = 잔액×연율%/1200(=/100/12). DIS-INT-RATE는 PIC S9(4)V99(연이자율 %, 예 12.50), TRAN-CAT-BAL/WS-MONTHLY-INT/WS-TOTAL-INT는 S9(9)V99 → Java BigDecimal scale=2, 나눗셈은 RoundingMode 명시 필수(COBOL COMPUTE 기본은 truncate). 이자거래는 1300-B-WRITE-TX가 TRAN-ID(STRING PARM-DATE+6자리 시퀀스), TYPE='01'/CAT='05', TRAN-SOURCE='System', DB2 타임스탬프(Z-GET-DB2-FORMAT-TIMESTAMP가 FUNCTION CURRENT-DATE를 EEEE-MM-DD-UU.MM.SS.HH0000 포맷으로 조립)를 채워 TRANSACT에 WRITE. 1400-COMPUTE-FEES는 EXIT만 있는 미구현 스텁. Spring Batch 대응: TCATBALF=ItemReader, control-break=계정 단위 청크 경계(또는 그룹 집계), 이자거래 WRITE=ItemWriter, ACCTFILE REWRITE=별도 업데이트.

== 선택적 모듈은 베이스 코드 수정 없이 "추가"로 통합 ==
각 모듈은 자체 cbl/cpy/csd를 갖고, CSD에서 동일한 GROUP(CARDDEMO)에 새 PROGRAM/TRANSACTION/(DB2ENTRY·DB2TRAN)을 추가 정의한다. 베이스와의 유일한 접점은 메뉴 옵션 테이블에 항목 추가:
- DB2 거래유형(app-transaction-type-db2): 관리자 메뉴 COADM02Y에 옵션 5/6(COTRTLIC=CTLI, COTRTUPC=CTTU) 추가. 프로그램은 `EXEC SQL`(DECLARE CURSOR FORWARD/BACKWARD, FETCH INTO :host-var, SELECT COUNT, UPDATE/DELETE, OPEN/CLOSE) 사용. DCLGEN copybook(dcl/DCLTRTYP.dcl)이 테이블↔COBOL 호스트변수 매핑. VARCHAR은 49-level len+text 쌍으로 표현. CSD에 DB2ENTRY/DB2TRAN(PLAN=CARDDEMO) 정의.
- IMS/DB2/MQ 권한승인(app-authorization-ims-db2-mq): 일반 메뉴 COMEN02Y 옵션 11(COPAUS0C=CPVS)로 노출. COPAUA0C(CP00)는 BMS 화면 없이 MQ 트리거로 기동. IMS 접근은 `EXEC DLI`(GU/GNP/ISRT/REPL, PCB 사용) — 계층형 DB. DB2는 사기(fraud) 기록. IMS+DB2 2단계 커밋 시연.
- VSAM/MQ 계정추출(app-vsam-mq): 메뉴에 없고 CDRD(CODATE01)/CDRA(COACCT01) 트랜잭션으로 직접 기동. MQ 패턴: 트리거 메시지 → `EXEC CICS RETRIEVE`로 트리거 큐명 획득 → `CALL 'MQOPEN'/'MQGET'/'MQPUT'/'MQCLOSE'`. MQ 상수/구조는 IBM copybook(CMQV, CMQODV, CMQGMOV 등)을 COPY. 요청/응답 비동기 패턴.
