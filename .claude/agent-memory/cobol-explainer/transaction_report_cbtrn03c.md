---
name: transaction-report-cbtrn03c
description: CBTRN03C transaction detail report batch — date-range filter, 4-file lookup join, card-level control-break totals, and the CVTRA07Y report-layout copybook
metadata:
  type: project
---

거래 상세 리포트 배치 = CBTRN03C (헤더 "Print the transaction detail report"). 표준 CB* 배치 골격([[batch-and-optional-modules]]) 위에 "리포트 생성" 변형을 얹은 케이스. 동반 JCL은 미확인.

6개 파일: 입력 TRANSACT(ASSIGN TRANFILE, 순차=드라이빙), DATEPARM(ASSIGN DATEPARM, 순차=일자범위 파라미터 1줄), 그리고 RANDOM 룩업용 CARDXREF/TRANTYPE/TRANCATG(모두 VSAM KSDS). 출력 REPORT-FILE(ASSIGN TRANREPT, 순차 인쇄 파일, FD-REPTFILE-REC PIC X(133)).

핵심 패턴 3가지:
1. 일자 필터: DATEPARM에서 WS-START-DATE/WS-END-DATE(둘 다 X(10)) 읽고, 각 거래의 TRAN-PROC-TS(1:10)가 범위 안일 때만 리포트 포함. 날짜를 문자열 사전식 비교(ISO yyyy-MM-dd 가정). 범위 밖이면 NEXT SENTENCE로 거래 처리블록 스킵 — NEXT SENTENCE는 다음 마침표까지 점프하는 구식 제어라 이식 시 함정.
2. 4파일 조인(룩업): 거래 1건마다 카드번호→CARDXREF(XREF-ACCT-ID), 유형코드→TRANTYPE(설명), 유형+카테고리코드→TRANCATG(설명)을 RANDOM READ. 세 룩업 모두 INVALID KEY면 9999-ABEND-PROGRAM(CALL 'CEE3ABD' ABCODE=999)로 hard-fail = 참조 무결성 위반을 치명오류로 간주.
3. control-break 합계: 그룹 키 = TRAN-CARD-NUM(라벨은 "Account Total"이라 1계정-1카드 가정 냄새). 카드 변경 시 직전 카드 소계 출력, MOD(WS-LINE-COUNTER, WS-PAGE-SIZE=20)=0이면 페이지합계+헤더 재출력(페이지네이션). 페이지합계는 grand-total에 누적. 잠재 이슈(추측): EOF 분기에서 마지막 계정 소계 누락 + 마지막 TRAN-AMT 중복 가산 가능 → 이식 시 출력 바이트 회귀검증 필요.

리포트 출력 레이아웃 copybook = CVTRA07Y(거래 리포트 전용, [[copybook-record-layouts]]에 없던 항목). 01 그룹들: REPORT-NAME-HEADER(타이틀+REPT-START/END-DATE), TRANSACTION-DETAIL-REPORT(거래ID/계정ID/유형코드+설명/카테고리코드+설명/소스/금액), TRANSACTION-HEADER-1(칼럼명), TRANSACTION-HEADER-2(PIC X(133) VALUE ALL '-' 구분선), REPORT-PAGE/ACCOUNT/GRAND-TOTALS(라벨+점선 FILLER+편집금액). 금액 편집 PIC: 상세는 -ZZZ,ZZZ,ZZZ.ZZ, 합계는 +ZZZ,ZZZ,ZZZ.ZZ. 합계 WS변수는 S9(09)V99 → Java BigDecimal scale=2, 편집PIC는 DecimalFormat/고정폭 포맷으로.
