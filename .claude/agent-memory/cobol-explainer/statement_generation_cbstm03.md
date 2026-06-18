---
name: statement-generation-cbstm03
description: Account statement batch CBSTM03A (driver) + CBSTM03B (I/O subroutine) — ALTER/GO TO dispatcher, generic file-handler CALL convention, 2-dim card/tran table join, dual text+HTML output
metadata:
  type: project
---

명세서 생성 배치 (job CREASTMT = app/jcl/CREASTMT.JCL). 2프로그램 분담 — modernization 도구 연습용으로 일부러 옛 관용구(ALTER, 제어블록 어드레싱, COMP/COMP-3, 2차원 배열, 서브루틴 CALL)를 모아둠(CBSTM03A 헤더 주석 30~35행 명시).

== 역할 분담 ==
- CBSTM03A.CBL = 드라이버/포매터. 비즈니스 로직 + 텍스트(STMTFILE, 80바이트) & HTML(HTMLFILE, 100바이트) 두 출력 작성. 자기 자신은 STMTFILE/HTMLFILE만 직접 OPEN, 데이터 4파일은 직접 안 엶.
- CBSTM03B.CBL = 범용 파일 핸들러 서브루틴. TRNXFILE/XREFFILE/CUSTFILE/ACCTFILE 4개 VSAM을 대신 OPEN/READ/CLOSE. PROCEDURE DIVISION USING LK-M03B-AREA 한 덩어리로 호출됨.

== CALL 규약 (CBSTM03A↔CBSTM03B) ==
공유 인터페이스 = WS-M03B-AREA(03A) ↔ LK-M03B-AREA(03B), 동일 레이아웃: DD명 X(8) + OPER X(1)(88: O/C/R/K/W/Z) + RC X(2) + KEY X(25) + KEY-LN S9(4) + FLDT X(1000). 03A가 DD명+오퍼레이션 코드 세팅 후 `CALL 'CBSTM03B' USING WS-M03B-AREA` → 03B가 EVALUATE LK-M03B-DD로 파일 분기, EVALUATE OPER로 OPEN/READ/CLOSE 분기, 결과를 파일상태 2바이트로 RC에 반환, 레코드는 FLDT(X1000 범용 버퍼)에 담아 돌려줌. 03A는 FLDT를 해당 copybook 01레벨(CARD-XREF-RECORD/CUSTOMER-RECORD/ACCOUNT-RECORD/TRNX-RECORD)로 MOVE해 해석. = Java의 단일 DAO 파사드 `byte[] io(String file, char op, String key)` + 호출측 역직렬화. RC '00'/'04'=정상, '10'=EOF.
READ-K(키 읽기)는 KEY(1:KEY-LN)를 잘라 레코드키로 MOVE 후 READ — 가변 키길이를 부분참조로 처리.

== 비자명 관용구: ALTER + GO TO 디스패처 (CBSTM03A 0000-START, 296행~) ==
WS-FL-DD 값에 따라 `ALTER 8100-FILE-OPEN TO PROCEED TO 8xxx-...-OPEN` 후 `GO TO 8100-FILE-OPEN`. ALTER는 GO TO의 타깃을 런타임에 바꿔치기 — 자기수정 코드. 각 OPEN 단락은 끝에서 WS-FL-DD를 다음 단계로 바꾸고 `GO TO 0000-START`로 복귀 → 상태머신을 GO TO로 돌리는 구조. 흐름: TRNXFILE open→READTRNX(전체 거래를 테이블에 적재)→XREFFILE→CUSTFILE→ACCTFILE→1000-MAINLINE. **Java 매핑: ALTER/GO TO는 직접 대응 없음** — switch 기반 state machine 또는 순차 메서드 호출로 평탄화. ALTER는 거의 모든 현대 표준/린터가 금지(추적 불가). 절대 그대로 옮기지 말고 명시적 제어흐름으로 재작성.

== 파일 조인 흐름 (메모리 내 2차원 테이블 = nested loop join) ==
1) 8500-READTRNX: TRNXFILE(거래, 카드번호로 정렬됨) 전체를 WS-TRNX-TABLE에 적재. 구조 = WS-CARD-TBL OCCURS 51 (카드별) { WS-CARD-NUM + WS-TRAN-TBL OCCURS 10 (카드당 거래) }. 즉 [카드][거래] 2차원. 카드 바뀌면 카드 카운터 CR-CNT++, 카드별 거래수는 WS-TRCT(CR-CNT)에 기록. = `Map<CardNum, List<Tran>>` 또는 `Tran[51][10]`.
2) 1000-MAINLINE: XREFFILE을 순차로 돌며(카드↔고객↔계정 교차참조) 각 건마다 CUSTFILE/ACCTFILE을 키로 GET(03B 경유) → 5000-CREATE-STATEMENT(헤더/고객/계정 라인) → 4000-TRNXFILE-GET이 적재된 2차원 테이블에서 해당 카드의 거래들을 찾아(PERFORM VARYING CR-JMP/TR-JMP 중첩 루프) 명세서 본문에 출력하고 WS-TOTAL-AMT 합산. = XREF 드라이빙 + 메모리 테이블 조인.

== 출력 포맷 (2종 동시) ==
- 텍스트(STMTFILE): STATEMENT-LINES 01그룹의 고정폭 ST-LINEn을 채워 WRITE. 금액은 편집필드 PIC 9(9).99- / Z(9).99-(Z=zero suppress, 후행 '-'=음수부호). START/END OF STATEMENT 배너.
- HTML(HTMLFILE): HTML-LINES에 88레벨 상수로 고정 태그(<html>,<table>,<tr>,<td> 등 인라인 CSS 포함)를 정의, `SET HTML-Lxx TO TRUE` 후 WRITE로 한 줄씩. 가변부(계정/이름/주소/거래)는 STRING으로 `<p>...</p>` 조립. = 템플릿 없이 코드로 HTML 직접 생성(현대라면 Thymeleaf/JSP로 대체).

데이터 구조 gotcha: 금액은 COMP-3(WS-TOTAL-AMT S9(9)V99)→BigDecimal scale2. 카운터는 COMP(S9(4) 2바이트 binary)→short/int. OCCURS 상한(51 카드/10 거래)은 하드코딩 — 초과 시 테이블 오버런(경계검사 없음), Java List로 풀면 제약 해소되나 원본 한계로 데이터 가정 확인 필요. CBSTM03A는 PSA/TCB/TIOT 제어블록 어드레싱(SET ADDRESS OF, POINTER, 240행~)으로 실행 중 JCL job명/DD명을 읽는데 이는 z/OS 내부구조 의존 — Java엔 대응 없음(환경변수/설정으로 대체). 관련: [[batch-and-optional-modules]], [[copybook-record-layouts]].
