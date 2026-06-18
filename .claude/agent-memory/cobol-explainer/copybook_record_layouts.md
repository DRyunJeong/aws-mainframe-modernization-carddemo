---
name: copybook-record-layouts
description: Canonical VSAM record-layout copybooks in app/cpy and which programs/files use them, plus the symbolic-map copybook convention
metadata:
  type: project
---

CardDemo의 VSAM 파일 레코드 레이아웃은 모두 `app/cpy/`의 copybook으로 정의되며, 온라인(CICS)과 배치 프로그램이 동일 copybook을 공유한다(공유 DTO/스키마 역할).

핵심 매핑(파일 ↔ copybook ↔ 01 레벨명):
- 계정 ACCTDATA(RECLN 300) ↔ `CVACT01Y` ↔ `ACCOUNT-RECORD` (ACCT-ID PIC 9(11) 키)
- 카드 CARDDATA(150) ↔ `CVACT02Y` ↔ CARD-RECORD
- 고객 CUSTDATA(500) ↔ `CVCUS01Y` (규범) / `CUSTREC` (CBSTM03A 전용 복사본 — `CUST-DOB-YYYYMMDD` vs `CUST-DOB-YYYY-MM-DD` 필드명 한 글자 차이, 바이트 레이아웃 동일)
- 카드/계정/고객 교차참조 CARDXREF(50) ↔ `CVACT03Y` ↔ `CARD-XREF-RECORD` (XREF-CARD-NUM, XREF-ACCT-ID, XREF-CUST-ID)
- 온라인 거래 TRANSACT KSDS(350) ↔ `CVTRA05Y` ↔ `TRAN-RECORD`
- 일일 거래 DALYTRAN(350) ↔ `CVTRA06Y` ↔ `DALYTRAN-RECORD` (TRAN-RECORD과 필드 구조 동일, 접두사만 DALYTRAN-)
- 거래 카테고리 잔액 TCATBALF(50) ↔ `CVTRA01Y` ↔ TRAN-CAT-BAL-RECORD (키: ACCT-ID + TYPE-CD + CAT-CD)
- 거래 카테고리 TRANCATG(60) ↔ `CVTRA04Y`, 거래 유형 TRANTYPE(60) ↔ `CVTRA03Y`, 공시그룹 DISCGRP(50) ↔ `CVTRA02Y`
- 사용자 보안 USRSEC(80) ↔ `CSUSR01Y` ↔ `SEC-USER-DATA` (SEC-USR-ID/PWD/TYPE; TYPE 'A'=admin,'U'=user)

브랜치 이전(export/import) 통합 copybook:
- 익스포트 순차파일(LRECL=500, RECFM=FB) ↔ `CVEXPORT` ↔ `EXPORT-RECORD`
  - 헤더 40바이트: EXPORT-REC-TYPE(1) + EXPORT-TIMESTAMP(26, REDEFINES로 DATE/SEP/TIME 분리) + EXPORT-SEQUENCE-NUM(4, COMP) + EXPORT-BRANCH-ID(4) + EXPORT-REGION-CODE(5)
  - 페이로드 460바이트: EXPORT-RECORD-DATA에 5종 REDEFINES — EXPORT-CUSTOMER-DATA / EXPORT-ACCOUNT-DATA / EXPORT-TRANSACTION-DATA / EXPORT-CARD-XREF-DATA / EXPORT-CARD-DATA
  - 각 REDEFINES는 CVCUS01Y·CVACT01Y·CVTRA05Y·CVACT03Y·CVACT02Y 원본 레이아웃을 반영하되, 오프셋 차이 주의(CVACT01Y ACCT-ID는 원본 DISPLAY지만 EXPORT-XREF-ACCT-ID는 COMP)
  - DISPLAY vs COMP 혼용(예: EXP-CUST-ID COMP=4B vs EXP-XREF-CUST-ID DISPLAY=9B), FILLER 크기 불균형(카드교차참조 427바이트 패딩)
  - 오탈자: EXPIRAION(계정·카드 만료일 필드) — 원본 그대로 보존
  - EXP-CARD-CVV-CD 평문 저장 → 마이그레이션 시 PCI-DSS 위반 위험
  - EXPORT-REC-TYPE 실제 코드값은 이 copybook에 정의 없음 — CBEXPORT/CBIMPORT 소스 확인 필요

공용 인프라 copybook:
- `COCOM01Y` = `CARDDEMO-COMMAREA` (프로그램 간 commarea / 세션 상태 객체). CDEMO-FROM/TO-PROGRAM, CDEMO-USER-TYPE(88 CDEMO-USRTYP-ADMIN='A'), CDEMO-PGM-CONTEXT(88 CDEMO-PGM-REENTER) 포함.
- `COMEN02Y` = 일반 사용자 메뉴 옵션 테이블, `COADM02Y` = 관리자 메뉴 옵션 테이블. 둘 다 FILLER로 채운 데이터 뒤에 `REDEFINES ... OCCURS`로 배열 뷰를 얹는 패턴(메뉴 항목 = 번호+이름+PGMNAME(+USRTYPE)).

화면(symbolic map) copybook 컨벤션: BMS 소스(`app/bms/*.bms`)에서 생성된 `app/cpy-bms/*.CPY`. 각 화면 필드 FOO마다 입력구조(...I)에 FOOL(길이/커서 S9(4) COMP), FOOF(속성/플래그), FOOI(입력값), 출력구조(...O, REDEFINES)에 FOOC/FOOP/FOOH/FOOV(색상/속성 등)와 FOOO(출력값)가 생성됨. 예: COSGN00.bms → COSGN00.CPY의 COSGN0AI/COSGN0AO.
