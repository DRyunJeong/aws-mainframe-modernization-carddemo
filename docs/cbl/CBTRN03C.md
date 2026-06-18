# CBTRN03C — 거래 상세 리포트 출력 배치

- **유형**: 배치 COBOL
- **한 줄 요약**: 일자 파라미터(시작일~종료일) 범위의 게시(posted) 거래를 순차로 읽어 4개 참조 파일과 조인하고, 카드번호 단위 control-break로 페이지/계정/총합계를 매겨 거래 상세 리포트(133바이트 인쇄 파일)를 생성한다.

## 기능 설명

CBTRN03C는 헤더 주석 그대로 "Print the transaction detail report"(L5)를 수행하는 인쇄용 리포트 배치다. CICS를 거치지 않고 `FILE-CONTROL`의 SELECT...ASSIGN으로 파일을 직접 연다(L28~57). 처리 골격은 CardDemo 표준 배치 패턴(OPEN 단락들 → `PERFORM UNTIL END-OF-FILE='Y'` 루프 → CLOSE 단락들 → GOBACK, L161~217)을 따른다.

핵심 동작은 세 가지다.
1. **일자 필터링**: `DATEPARM` 파일에서 시작일/종료일을 한 줄 읽어(L221), 각 거래의 처리 타임스탬프 앞 10자리(`TRAN-PROC-TS (1:10)`)가 그 범위 안일 때만 리포트에 싣는다(L173~174).
2. **참조 데이터 조인(룩업)**: 거래 1건마다 카드번호로 `CARDXREF`(계정 ID 획득), 거래유형코드로 `TRANTYPE`(유형 설명), 유형코드+카테고리코드로 `TRANCATG`(카테고리 설명)를 RANDOM READ로 조회한다(L187~195).
3. **합계 집계(control-break)**: 카드번호가 바뀌면 직전 카드의 "Account Total"을 출력하고(L181~188), 페이지가 20행 차면 "Page Total"을 출력하며(L282~285), EOF 시 마지막 페이지·총합계를 출력한다(L197~204).

> 주의: 리포트 그룹핑 키는 "카드번호"(`TRAN-CARD-NUM`)이지만 합계 라벨은 "Account Total"이다. 한 계정에 카드가 여러 장이면 카드 단위로 소계가 끊긴다(추측: 대부분 1계정-1카드 가정으로 설계된 것으로 보임).

## 입력 / 출력

- **입력**:
  - `TRANSACT-FILE` (ASSIGN `TRANFILE`, 순차) — 게시된 거래 레코드. 레코드 레이아웃은 `CVTRA05Y`의 `TRAN-RECORD`. FD에서는 `FD-TRANS-DATA X(304)` + `FD-TRAN-PROC-TS X(26)` + `FD-FILLER X(20)` = 350바이트로 봄(L62~65). 메모리 기준 TRANSACT는 RECLN 350.
  - `XREF-FILE` (ASSIGN `CARDXREF`, INDEXED/RANDOM, 키 `FD-XREF-CARD-NUM` X(16)) — 카드↔계정↔고객 교차참조. 레이아웃 `CVACT03Y`의 `CARD-XREF-RECORD`(L33~37, 98).
  - `TRANTYPE-FILE` (ASSIGN `TRANTYPE`, INDEXED/RANDOM, 키 `FD-TRAN-TYPE` X(02)) — 거래유형 마스터. 레이아웃 `CVTRA03Y`(L39~43, 103).
  - `TRANCATG-FILE` (ASSIGN `TRANCATG`, INDEXED/RANDOM, 키 `FD-TRAN-CAT-KEY` = 유형코드 X(02) + 카테고리코드 9(04)) — 거래카테고리 마스터. 레이아웃 `CVTRA04Y`(L45~49, 108).
  - `DATE-PARMS-FILE` (ASSIGN `DATEPARM`, 순차) — 리포트 일자 범위 파라미터. `WS-DATEPARM-RECORD` = 시작일 X(10) + 구분자 X(01) + 종료일 X(10)으로 파싱(L122~125, 221).
- **출력**:
  - `REPORT-FILE` (ASSIGN `TRANREPT`, OUTPUT 순차) — 인쇄용 리포트 파일. 레코드는 `FD-REPTFILE-REC PIC X(133)`(L85). 모든 출력 라인 형식(헤더/상세/페이지·계정·총합계)은 `CVTRA07Y`에 정의(L113).
  - `DISPLAY` 로그: 시작/종료 배너, "Reporting from ... to ...", 디버그용 `TRAN-RECORD` / `TRAN-AMT` / `WS-PAGE-TOTAL` 덤프(L160, 180, 198~199, 215) → SYSOUT로 나감.

## 의존성

- **COPY (카피북)**:
  - `CVTRA05Y` — 거래 레코드(`TRAN-RECORD`: TRAN-ID, TRAN-CARD-NUM, TRAN-TYPE-CD, TRAN-CAT-CD, TRAN-SOURCE, TRAN-AMT, TRAN-PROC-TS 등) (L93)
  - `CVACT03Y` — 카드 교차참조(`CARD-XREF-RECORD`: XREF-ACCT-ID 등) (L98)
  - `CVTRA03Y` — 거래유형(`TRAN-TYPE-RECORD`: TRAN-TYPE-DESC) (L103)
  - `CVTRA04Y` — 거래카테고리(`TRAN-CAT-RECORD`: TRAN-CAT-TYPE-DESC) (L108)
  - `CVTRA07Y` — 리포트 출력 레이아웃(REPORT-NAME-HEADER, TRANSACTION-DETAIL-REPORT, TRANSACTION-HEADER-1/2, REPORT-PAGE/ACCOUNT/GRAND-TOTALS) (L113)
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CALL 'CEE3ABD'` — LE(Language Environment) 강제 abend 서비스. `9999-ABEND-PROGRAM`에서 ABCODE=999로 호출(L630). 그 외 애플리케이션 서브프로그램 CALL/XCTL/LINK 없음.
- **데이터셋/파일/DB 테이블**:
  - 입력: `TRANFILE`(순차), `CARDXREF`(VSAM KSDS), `TRANTYPE`(VSAM KSDS), `TRANCATG`(VSAM KSDS), `DATEPARM`(순차)
  - 출력: `TRANREPT`(순차 인쇄 파일)
  - DB2/IMS 등 DB 테이블 사용 없음.
- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID 없음(배치 프로그램). JCL의 `EXEC PGM=CBTRN03C`로 기동되며 DD명(TRANFILE/CARDXREF/TRANTYPE/TRANCATG/TRANREPT/DATEPARM)이 위 ASSIGN 명과 연결됨(추측: 동반 JCL은 거래 리포트 잡, 미확인).

## 핵심 로직 흐름

1. **초기화/OPEN** — 시작 배너 DISPLAY 후 6개 파일을 차례로 OPEN: 0000(TRANFILE INPUT) → 0100(REPORT OUTPUT) → 0200(CARDXREF INPUT) → 0300(TRANTYPE INPUT) → 0400(TRANCATG INPUT) → 0500(DATEPARM INPUT) (L161~166). 각 OPEN 단락은 FILE STATUS≠'00'이면 `9910-DISPLAY-IO-STATUS` 후 `9999-ABEND-PROGRAM`으로 abend.
2. **일자 파라미터 읽기** — `0550-DATEPARM-READ`가 한 줄을 `WS-DATEPARM-RECORD`로 읽어 시작/종료일을 세팅. status '00'→정상, '10'(EOF, 빈 파일)→END-OF-FILE='Y'로 즉시 종료, 그 외→abend(L220~243).
3. **메인 루프 (`PERFORM UNTIL END-OF-FILE='Y'`)** (L170~206):
   - `1000-TRANFILE-GET-NEXT`로 다음 거래를 READ. '00'→계속, '10'→END-OF-FILE='Y', 그 외→abend(L248~272).
   - **일자 필터**: `TRAN-PROC-TS(1:10)`가 [WS-START-DATE, WS-END-DATE] 범위면 CONTINUE, 아니면 `NEXT SENTENCE`로 이번 거래 처리 블록을 건너뜀(L173~178).
     - 주의(가독성): `NEXT SENTENCE`는 다음 마침표(.)까지 점프하는데, 여기서는 `END-PERFORM` 직전 마침표까지 가므로 사실상 "이 거래 스킵" 효과. 현대 COBOL의 `CONTINUE`와 의미가 다르며 유지보수 시 함정(아래 Java 노트 참조).
   - **EOF 아닐 때(정상 거래)**:
     - 디버그용 `DISPLAY TRAN-RECORD`.
     - **control-break(카드번호 변경)**: `WS-CURR-CARD-NUM ≠ TRAN-CARD-NUM`이면 — 최초가 아니면(`WS-FIRST-TIME='N'`) `1120-WRITE-ACCOUNT-TOTALS`로 직전 카드 소계 출력 → 현재 카드번호를 WS/룩업키에 저장 → `1500-A-LOOKUP-XREF`로 계정ID 조회(L181~188).
     - **유형/카테고리 룩업**: 유형코드로 `1500-B-LOOKUP-TRANTYPE`, 유형+카테고리코드로 `1500-C-LOOKUP-TRANCATG` 수행(L189~195). 세 룩업 모두 `INVALID KEY`면 즉시 abend(참조 무결성 위반을 치명 오류로 간주, L484~512).
     - `1100-WRITE-TRANSACTION-REPORT` 호출(L196).
   - **EOF일 때(루프 마지막)**: 마지막 거래의 잔여 처리 — `TRAN-AMT`를 페이지/계정 합계에 더하고 `1110-WRITE-PAGE-TOTALS` + `1110-WRITE-GRAND-TOTALS` 출력(L197~204).
     - 주의/잠재버그(추측): EOF 분기에서 직전 카드의 `1120-WRITE-ACCOUNT-TOTALS`는 명시 호출되지 않아 마지막 계정 소계가 누락될 수 있음. 또한 EOF 시점엔 `TRAN-RECORD`가 갱신되지 않아(READ가 EOF) 마지막 `TRAN-AMT`가 중복 가산될 소지가 있음. 런타임 검증 필요.
4. **`1100-WRITE-TRANSACTION-REPORT`** (L274~290): 최초 1회면 헤더 일자 세팅 후 `1120-WRITE-HEADERS`. 이후 `MOD(WS-LINE-COUNTER, WS-PAGE-SIZE=20)=0`이면 페이지 합계+새 헤더 출력(페이지네이션). 그다음 `TRAN-AMT`를 페이지/계정 합계에 누적하고 `1120-WRITE-DETAIL`로 상세 한 줄 기록.
5. **상세 라인 포맷팅(`1120-WRITE-DETAIL`, L361~374)**: `TRANSACTION-DETAIL-REPORT`를 INITIALIZE 후 거래ID/계정ID/유형코드+설명/카테고리코드+설명/소스/금액을 채워 133바이트 레코드로 WRITE. 금액은 `TRAN-REPORT-AMT PIC -ZZZ,ZZZ,ZZZ.ZZ`로 편집(부호+천단위콤마+소수2자리).
6. **합계 단락들**: 페이지합계는 grand-total에 누적 후 리셋(L297~298), 계정합계는 출력 후 리셋(L310). 모든 출력은 `1111-WRITE-REPORT-REC`로 단일화(WRITE 후 status≠'00'이면 abend, L343~359).
7. **종료** — 6개 파일 CLOSE(9000~9500) → 종료 배너 → `GOBACK`(L208~217).

## Java/현대화 노트

- **전체 패턴 → Spring Batch 리포트 잡**: `TRANSACT-FILE` 순차 스캔 = `FlatFileItemReader`(또는 거래가 KSDS면 VSAM 어댑터). 4개 참조 파일 RANDOM READ = 룩업 캐시/JPA 조회 또는 사전 로딩된 `Map`. 리포트 라인 생성 = `FlatFileItemWriter` + 커스텀 `LineAggregator`. control-break는 Spring Batch에 내장 개념이 없으므로 `ItemProcessor`/리스너에서 직전 키를 보관하며 그룹 경계를 직접 감지하거나, 정렬된 스트림에 `Collectors.groupingBy(card)`로 집계.
- **일자 파라미터 파일 → Job Parameter**: `DATEPARM` 한 줄 읽기는 자바에선 잡 파라미터(`startDate`, `endDate`) 또는 설정으로 대체. COBOL은 날짜를 `X(10)` 문자열로 비교(`TRAN-PROC-TS(1:10)` 사전식 비교)하는데, ISO `yyyy-MM-dd`라면 문자열 비교가 곧 날짜 순서와 일치한다(설계 의도). 자바에서는 `LocalDate` 파싱 후 `isBefore/isAfter`로 명시 비교 권장 — 단, 입력 포맷이 ISO가 아니면 사전식 비교가 깨지므로 포맷 보장 필요.
- **금액 정밀도 → BigDecimal**: 합계 변수 `WS-PAGE-TOTAL`/`WS-ACCOUNT-TOTAL`/`WS-GRAND-TOTAL`은 `PIC S9(09)V99`(부호有, 정수9+소수2, DISPLAY). `TRAN-AMT`도 통화 금액이므로 `double`이 아닌 `BigDecimal`(scale=2)로 매핑하고 `add`로 누적. COBOL `ADD ... TO A B`는 한 소스를 두 대상에 동시 가산하는 관용구로, 자바에선 `pageTotal=pageTotal.add(amt); accountTotal=accountTotal.add(amt);` 두 줄로 분해.
- **편집 PIC → 포맷터**: `TRAN-REPORT-AMT PIC -ZZZ,ZZZ,ZZZ.ZZ`, 합계의 `+ZZZ,ZZZ,ZZZ.ZZ`는 부호·천단위콤마·선행공백억제를 가진 화면 표현이다. 자바에서는 `DecimalFormat`(예: `"#,##0.00;-#,##0.00"`) 또는 리포트 템플릿으로 재현. 133바이트 고정폭 라인은 `String.format`/패딩으로 칼럼 정렬.
- **고정길이 레코드 / 1-based substring**: `TRAN-PROC-TS (1:10)`은 1부터 시작하는 부분문자열(처음 10바이트). 자바는 0-based `substring(0, 10)`. 인덱스 오프바이원 주의.
- **`NEXT SENTENCE` 함정**: 위 흐름의 일자 필터에서 쓰인 `NEXT SENTENCE`는 "다음 if가 아니라 다음 마침표까지" 점프하는 구식 제어로, 의도치 않은 코드 스킵을 유발하기 쉽다. 자바 이식 시에는 단순 `if (inRange) { ...처리... }` (또는 `continue`로 루프 다음 반복)로 명확히 대체할 것. 이식 전 실제 흐름을 테스트로 고정(characterization test)할 것을 권장.
- **참조 무결성 = 예외 vs abend**: 세 룩업의 `INVALID KEY → 9999-ABEND-PROGRAM`은 "참조 깨지면 잡 전체 abend(ABCODE 999, `CALL 'CEE3ABD'`)" 정책이다. 자바에서는 잡 실패로 던지는 커스텀 예외(예: `ReferentialIntegrityException`)로 매핑하되, 운영 요건에 따라 "해당 거래만 skip + reject 리포트"로 완화할지 결정 필요(현 동작은 hard-fail).
- **`APPL-RESULT`/88-level 상태기계**: `APPL-AOK`(0), `APPL-EOF`(16) 같은 88-level은 자바 `enum IoResult { OK, EOF, ERROR }` 또는 boolean 술어로. `IO-STATUS-04`·`TWO-BYTES-BINARY REDEFINES`(L142~145)는 2자리 FILE STATUS를 사람이 읽는 4자리로 변환하는 표시용 트릭으로, 자바 로깅에서는 불필요(상태문자열 그대로 로깅).
- **확인 필요(런타임 검증 권장)**: (1) 마지막 계정 소계 누락 가능성, (2) EOF 시 `TRAN-AMT` 중복 가산 가능성, (3) `DATEPARM` 미연동 JCL의 정확한 DD/필터 동작. 이식 시 동일 입력에 대한 출력 바이트 비교(레거시 vs 신규)로 회귀 검증 권장.
