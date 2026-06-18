# CBSTM03A — 계좌 명세서 생성 배치 (드라이버/포매터)

- **유형**: 배치 COBOL
- **한 줄 요약**: 거래 파일을 카드↔고객↔계정 교차참조와 조인해 계좌별 월간 명세서를 plain text와 HTML 두 포맷으로 동시에 생성하는 배치 드라이버 (`Function : Print Account Statements ... in two formats : 1/plain text and 2/HTML`, 8~9행).

## 기능 설명

CardDemo의 명세서 생성 배치의 메인 프로그램이다. 거래(TRNXFILE)를 메모리 내 2차원 테이블에 전부 적재한 뒤, 교차참조(XREFFILE)를 순차로 돌며 각 카드에 대응하는 고객(CUSTFILE)·계정(ACCTFILE) 레코드를 키로 읽어, 계정마다 명세서 한 건을 텍스트(`STMTFILE`)와 HTML(`HTMLFILE`)로 동시에 출력한다.

헤더 주석(30~35행)에 명시되어 있듯, 이 프로그램은 modernization 도구를 시험하기 위한 **레거시 관용구 모음**이다. 의도적으로 다음을 포함한다.

1. 메인프레임 제어블록 어드레싱 (PSA/TCB/TIOT, 30행)
2. `ALTER`와 `GO TO` 문 (31행)
3. `COMP`/`COMP-3` 변수 (32행)
4. 2차원 배열 (33행)
5. 서브루틴 호출 (34행)

실제 파일 I/O는 직접 하지 않고, 범용 파일 핸들러 서브루틴 `CBSTM03B`에 위임한다. CBSTM03A 자신은 출력 2파일(`STMT-FILE`, `HTML-FILE`)만 직접 `OPEN`/`WRITE`/`CLOSE`한다(293행, 339행).

## 입력 / 출력

- **입력**:
  - `TRNXFILE` — 거래 파일(카드번호 정렬 가정). CBSTM03B 경유로 읽어 2차원 테이블에 전체 적재 (8500-READTRNX-READ, 818행~).
  - `XREFFILE` — 카드↔고객↔계정 교차참조 파일. 순차 READ로 명세서 생성을 주도 (1000-XREFFILE-GET-NEXT, 345행).
  - `CUSTFILE` — 고객 마스터. `XREF-CUST-ID`를 키로 랜덤 READ (2000-CUSTFILE-GET, 368행).
  - `ACCTFILE` — 계정 마스터. `XREF-ACCT-ID`를 키로 랜덤 READ (3000-ACCTFILE-GET, 392행).
  - 위 4개는 모두 CBSTM03B를 통해 간접 접근(직접 `SELECT` 없음).
- **출력**:
  - `STMTFILE` — plain text 명세서 (`FD-STMTFILE-REC PIC X(80)`, 80바이트 고정폭, 44~45행).
  - `HTMLFILE` — HTML 명세서 (`FD-HTMLFILE-REC PIC X(100)`, 100바이트 고정폭, 46~47행).
  - z/OS 진단용 `DISPLAY` 출력 — 실행 중인 JCL job명/step명/DD명 목록 (270, 275~291행). 이는 산출물이 아니라 SYSOUT 로그.

## 의존성

- **COPY (카피북)** (51~57행):
  - `COSTM01` — 거래 레코드 레이아웃(`TRNX-RECORD`, `TRNX-CARD-NUM`/`TRNX-ID`/`TRNX-REST`/`TRNX-DESC`/`TRNX-AMT` 등). 본문에서 `TRNX-*` 필드로 참조.
  - `CVACT03Y` — 카드 교차참조 레코드(`CARD-XREF-RECORD`, `XREF-CARD-NUM`/`XREF-CUST-ID`/`XREF-ACCT-ID`).
  - `CUSTREC` — 고객 레코드(`CUSTOMER-RECORD`, `CUST-FIRST-NAME`/`CUST-ADDR-*`/`CUST-FICO-CREDIT-SCORE` 등).
  - `CVACT01Y` — 계정 레코드(`ACCOUNT-RECORD`, `ACCT-ID`/`ACCT-CURR-BAL`).
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CALL 'CBSTM03B' USING WS-M03B-AREA` — 범용 파일 핸들러 서브루틴. TRNXFILE/XREFFILE/CUSTFILE/ACCTFILE의 OPEN/READ/READ-K/CLOSE를 대행 (351, 377, 401, 734, 746, 769, 787, 805, 835, 860, 877, 893, 909행 등 다수).
  - `CALL 'CEE3ABD'` — 비정상 종료 시 LE(Language Environment) 강제 abend 서비스 (9999-ABEND-PROGRAM, 923행).
- **데이터셋/파일/DB 테이블**:
  - DD명: `STMTFILE`, `HTMLFILE`(직접 출력), `TRNXFILE`, `XREFFILE`, `CUSTFILE`, `ACCTFILE`(CBSTM03B 경유 입력). DB2/SQL 없음(전부 VSAM/QSAM 추정).
- **트랜잭션 ID 또는 EXEC PGM**:
  - 온라인 트랜잭션 아님(CICS 명령 없음). 배치로 `EXEC PGM=CBSTM03A`로 기동됨 (추측: JCL은 본 소스에 없음; 메모리상 `CREASTMT`/`CREASTMT.JCL`로 기록되어 있으나 미확인이므로 추측).

## 핵심 로직 흐름

### 0. 제어블록 어드레싱 (PROCEDURE 진입, 266~291행)
LINKAGE의 `PSA-BLOCK`/`TCB-BLOCK`/`TIOT-BLOCK`을 `SET ADDRESS OF`로 실제 z/OS 제어블록에 매핑해, 실행 중인 JCL job명(`TIOTNJOB`)·step명(`TIOTJSTP`)을 출력하고, TIOT를 순회하며 할당된 DD명(`TIOCDDNM`)과 UCB 유효성을 나열한다. 비즈니스 로직과 무관한 환경 진단 코드다.

### 1. 초기화 (293~294행)
`OPEN OUTPUT STMT-FILE HTML-FILE` 후 2차원 테이블 `WS-TRNX-TABLE`와 카운터 테이블 `WS-TRN-TBL-CNTR`를 `INITIALIZE`.

### 2. ALTER/GO TO 상태머신: 파일 오픈 + 거래 적재 (0000-START, 296~314행)
`WS-FL-DD` 값으로 분기하는 **자기수정 상태머신**. 각 분기는 `ALTER 8100-FILE-OPEN TO PROCEED TO 8xxx-...-OPEN` 후 `GO TO 8100-FILE-OPEN`을 실행한다. 각 OPEN 단락은 끝에서 `WS-FL-DD`를 다음 단계 값으로 바꾸고 `GO TO 0000-START`로 되돌아온다. 진행 시퀀스:

```
TRNXFILE open + 첫 READ (8100, 730행) ─→ WS-FL-DD='READTRNX'
   └─→ READTRNX (8500, 818행): 거래 전건을 2차원 테이블에 적재 ─→ WS-FL-DD='XREFFILE'
       └─→ XREFFILE open (8200, 765행) ─→ WS-FL-DD='CUSTFILE'
           └─→ CUSTFILE open (8300, 783행) ─→ WS-FL-DD='ACCTFILE'
               └─→ ACCTFILE open (8400, 801행) ─→ GO TO 1000-MAINLINE
```

`ALTER ... PROCEED TO`는 `8100-FILE-OPEN`(726행) 단락 안의 `GO TO`의 분기 타깃을 **런타임에 바꿔치기**한다(자기수정 코드). 8100-FILE-OPEN의 정적 본문은 `GO TO 8100-TRNXFILE-OPEN`(727행)으로 적혀 있으나, ALTER에 의해 실제 점프 대상이 매 호출마다 교체된다.

#### 2a. 거래 2차원 테이블 적재 (8500-READTRNX-READ, 818~853행)
제어 브레이크(control break) 로직으로 거래를 카드별로 묶어 적재한다. `WS-SAVE-CARD`와 현재 `TRNX-CARD-NUM`을 비교해:
- 같으면 카드 내 거래 카운터 `TR-CNT`++
- 다르면 직전 카드의 거래수를 `WS-TRCT(CR-CNT)`에 확정 저장하고 카드 카운터 `CR-CNT`++, `TR-CNT`=1로 리셋

이렇게 `WS-CARD-TBL[CR-CNT]`의 카드번호와 `WS-TRAN-TBL[CR-CNT][TR-CNT]`의 거래를 채운다. 자기 자신을 `GO TO 8500-READTRNX-READ`로 다시 도는 루프(EOF '10'이면 8599-EXIT로 탈출, 841행). 즉 `[카드][거래]` 2차원 배열로 거래 파일을 메모리에 통째로 들고 있는다.

### 3. 메인 조인 루프 (1000-MAINLINE, 316~342행)
`END-OF-FILE='Y'`까지:
1. `1000-XREFFILE-GET-NEXT` — XREF 한 건 순차 READ(EOF면 종료).
2. `2000-CUSTFILE-GET` / `3000-ACCTFILE-GET` — XREF의 고객ID·계정ID로 마스터 랜덤 READ.
3. `5000-CREATE-STATEMENT` — 명세서 헤더/고객정보/계정 기본정보 라인 작성(텍스트+HTML).
4. `CR-JMP=1`, `WS-TOTAL-AMT=0` 후 `4000-TRNXFILE-GET` — 메모리 테이블에서 해당 카드의 거래들을 찾아 본문 출력 + 합산.

루프 종료 후 4개 입력 파일 CLOSE(9100~9400) + 출력 2파일 CLOSE 후 `9999-GOBACK`에서 `GOBACK`.

### 4. 메모리 테이블 조인 (4000-TRNXFILE-GET, 416~456행)
중첩 `PERFORM VARYING`으로 nested-loop join을 수행:
- 바깥 루프(`CR-JMP`): `WS-CARD-NUM(CR-JMP)`가 `XREF-CARD-NUM`과 일치하는 카드를 탐색. 카드 테이블이 정렬돼 있다는 가정 하에 `WS-CARD-NUM(CR-JMP) > XREF-CARD-NUM`이면 조기 중단(419행).
- 안쪽 루프(`TR-JMP`): 그 카드의 거래수 `WS-TRCT(CR-JMP)`만큼 거래를 꺼내 `6000-WRITE-TRANS`로 출력하고 `TRNX-AMT`를 `WS-TOTAL-AMT`에 누적(429행).

이후 합계를 `ST-TOTAL-TRAMT`에 편집해 텍스트 푸터(ST-LINE12/14A/15)와 HTML 종료 태그들을 WRITE한다.

### 5. 이중 출력 포맷
- **텍스트** (5000-CREATE-STATEMENT, 6000-WRITE-TRANS): `STATEMENT-LINES`(85행~)의 고정폭 `ST-LINEn` 그룹을 채워 `WRITE FD-STMTFILE-REC FROM ST-LINEn`. 금액은 편집필드 `PIC 9(9).99-`(현재잔액, 113행)·`PIC Z(9).99-`(거래/합계 금액, 137·142행)로 zero-suppress 및 후행 음수부호 처리. START/END OF STATEMENT 배너(ST-LINE0/15).
- **HTML** (5100-WRITE-HTML-HEADER, 5200-WRITE-HTML-NMADBS, 6000-WRITE-TRANS): 고정 태그는 `HTML-FIXED-LN`의 88레벨 상수(`<html>`/`<table>`/`<tr>`/`<td>` + 인라인 CSS, 150~211행)를 `SET HTML-Lxx TO TRUE` 후 WRITE. 가변부(계정번호·이름·주소·기본정보·거래)는 `STRING`으로 `<p>...</p>`를 조립해 WRITE(예: 562~567, 614~618, 687~691행). `DELIMITED BY '  '`(공백 2칸)로 우측 패딩을 잘라낸다.

## Java/현대화 노트

- **ALTER / GO TO 디스패처는 절대 직역 금지.** `ALTER ... PROCEED TO`는 런타임에 점프 타깃을 바꾸는 자기수정 코드로, 사실상 모든 현대 표준·린터가 금지하며 정적 추적이 불가능하다. `WS-FL-DD` 기반 상태머신은 **명시적 순차 메서드 호출**로 평탄화하는 것이 정답이다.
  ```java
  // ALTER/GO TO 상태머신 → 그냥 순차 호출로
  openAndLoadTransactions();   // 8100 + 8500: TRNXFILE open → 테이블 적재
  openXref();                  // 8200
  openCust();                  // 8300
  openAcct();                  // 8400
  generateStatements();        // 1000-MAINLINE
  ```
- **제어블록 어드레싱(PSA/TCB/TIOT)은 Java 대응 없음.** z/OS 내부 구조에 직접 의존해 job명·DD명을 읽는 코드로, 순수 진단 목적이다. Java에서는 환경변수·설정파일·`System.getProperty`/잡 메타데이터로 대체하고, 대부분은 그냥 삭제해도 비즈니스 로직에 무영향.
- **CALL 'CBSTM03B' = 단일 DAO 파사드.** 공유 인터페이스 `WS-M03B-AREA`(DD명 X(8) + OPER + RC + KEY + KEY-LN + FLDT X(1000))는 `byte[] io(String dd, char op, String key)` 형태의 파사드로 매핑된다. 반환 RC `'00'`/`'04'`=정상, `'10'`=EOF. 호출측은 범용 버퍼 `FLDT`(X1000)를 해당 레코드 DTO로 역직렬화한다(`MOVE WS-M03B-FLDT TO ...`). 현대화 시에는 파일별 타입세이프 Repository 4개로 나누는 편이 낫다. CBSTM03B 자체 분석은 별도 문서 필요.
- **2차원 테이블 = nested-loop in-memory join, 단 경계검사 없음.** `WS-CARD-TBL OCCURS 51` / `WS-TRAN-TBL OCCURS 10`은 카드 51개·카드당 거래 10건이 하드코딩 상한이다(226~228행). 초과 시 **테이블 오버런**(경계검사 없음). Java로는 `Map<String, List<Transaction>>`로 풀면 제약이 사라지지만, 원본의 이 가정이 데이터로 보장되는지 반드시 확인할 것. 또한 조인 조기중단(419행)은 카드 테이블·XREF가 카드번호로 정렬돼 있어야 성립하므로, 정렬 전제를 명시적으로 보존하거나 Map 조회로 대체해야 한다.
- **금액은 BigDecimal(scale 2) 고정.** `WS-TOTAL-AMT PIC S9(9)V99 COMP-3`(64~65행)는 packed decimal → `BigDecimal`로, `setScale(2)` 유지. `float`/`double` 절대 금지(정밀도 손실). 카운터 `CR-CNT`/`TR-CNT`/`CR-JMP`/`TR-JMP`/`WS-TRCT`는 `PIC S9(4) COMP`(2바이트 binary) → `short`/`int`.
- **편집필드 = 출력 포매팅 책임 분리.** `PIC 9(9).99-`·`PIC Z(9).99-`의 zero-suppress와 후행 음수부호는 `DecimalFormat`(예: `new DecimalFormat("#########0.00;0.00-")` 류)나 별도 포매터로 옮긴다. 표시 로직이므로 도메인 모델과 분리.
- **HTML을 코드로 직접 생성 → 템플릿 엔진으로.** 88레벨 상수 태그 + `STRING` 조립 방식은 유지보수가 어렵다. Thymeleaf/JSP/FreeMarker 같은 템플릿으로 대체하고, `STRING ... DELIMITED BY '  '`의 공백 트리밍은 `String.strip()`/`trim()`으로 단순화. 텍스트·HTML 두 포맷은 동일 명세서 모델에서 두 개의 뷰/렌더러로 분리하는 것이 자연스럽다.
- **고정길이 레코드 / 1-기반 인덱스 주의.** 출력 레코드는 80·100바이트 고정폭이며 COBOL 테이블은 1-기반(Java는 0-기반). 인덱스 변환과 우측 공백 패딩 처리에 유의.
- **이름 STRING 조립 시 중간이름 공백.** `5000-CREATE-STATEMENT`(462~469행)는 first/middle/last를 단일 공백으로 잇는데, 중간이름이 비면 공백이 이중으로 들어갈 수 있다(추측: 데이터에 따라). Java 이식 시 `Stream.of(...).filter(not blank).collect(joining(" "))`로 정리 권장.
