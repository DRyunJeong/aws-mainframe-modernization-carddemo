# CBACT04C — 이자 계산 배치 프로그램 (Interest Calculator)

- **유형**: 배치 COBOL (Batch COBOL Program)
- **한 줄 요약**: 거래 카테고리 잔액(TCATBALF)을 계정ID 순서로 순차 스캔하며 공시그룹(DISCGRP)의 연이자율로 카테고리별 월이자를 계산하고, 계정 단위로 합산한 이자를 계정 잔액에 반영(REWRITE)하며 이자 거래를 TRANSACT 파일에 생성하는 control-break 배치다.

---

## 기능 설명

이 프로그램은 야간 배치 시퀀스(INTCALC 잡)에서 실행되는 **이자 계산 엔진**이다. 신용카드 계정의 미결제 잔액에 대해 월이자를 계산하는 것이 목적이다(`Function : This is a interest calculator program.` L5).

처리 단위는 "거래 카테고리 잔액(transaction category balance)" 레코드다. 하나의 계정은 여러 카테고리(예: 구매, 현금서비스 등 TYPE-CD + CAT-CD 조합)별로 잔액을 나눠 보관하며, 각 카테고리마다 적용 이자율이 다를 수 있다. 프로그램은 이 카테고리 단위로 이자를 계산한 뒤, **계정 단위로 합산**하여 한 번에 계정 잔액에 반영한다.

핵심 설계는 **control-break(제어 분리) 패턴**이다. 드라이빙 파일 TCATBALF가 계정ID 오름차순으로 정렬되어 있다는 전제 하에, 같은 계정의 카테고리 레코드들이 연속으로 읽히는 동안 이자를 누적하고, 계정ID가 바뀌는 순간(=control break) 직전 계정의 누적 이자를 확정·반영한다. Java로 치면 정렬된 스트림을 순회하며 그룹 키가 바뀔 때 집계를 flush하는 group-by 집계와 같다.

---

## 입력 / 출력

- **입력**:
  - `TCATBAL-FILE`(DD `TCATBALF`): 거래 카테고리 잔액 KSDS. `ACCESS MODE IS SEQUENTIAL`(L30)로 열어 **계정ID 순으로 순차 스캔하는 드라이빙 파일**. 키 `FD-TRAN-CAT-KEY` = ACCT-ID(9(11)) + TYPE-CD(X(02)) + CAT-CD(9(04)) (L63-66).
  - `XREF-FILE`(DD `XREFFILE`): 카드↔계정 교차참조 KSDS. `ACCESS RANDOM`, **대체키 `FD-XREF-ACCT-ID`로 계정ID→카드번호 조회**(L34-39). 생성하는 이자 거래에 카드번호를 채우기 위해 사용.
  - `DISCGRP-FILE`(DD `DISCGRP`): 공시그룹(disclosure group) = **이자율 마스터 KSDS**. `ACCESS RANDOM`, 키 = ACCT-GROUP-ID(X(10)) + TRAN-TYPE-CD(X(02)) + TRAN-CAT-CD(9(04)) (L47-51, L78-82).
  - `EXTERNAL-PARMS`(LINKAGE, JCL PARM 수신): `PARM-LENGTH PIC S9(04) COMP` + `PARM-DATE PIC X(10)` (L176-178). 처리일자를 받아 이자 거래 ID의 접두사로 사용. JCL의 `PARM='YYYYMMDD...'`이 COBOL 진입점으로 전달되는 표준 패턴이며 `PROCEDURE DIVISION USING EXTERNAL-PARMS`로 수신(L180).

- **출력**:
  - `ACCOUNT-FILE`(DD `ACCTFILE`): 계정 마스터 KSDS. `OPEN I-O`(L291)로 열어 **READ 후 REWRITE**(L356) — 누적 이자를 잔액에 반영하므로 입출력 파일.
  - `TRANSACT-FILE`(DD `TRANSACT`): **순차(SEQUENTIAL) 출력 파일**(L53-56). `OPEN OUTPUT`(L309)로 열어 생성된 이자 거래를 WRITE(L500). 카테고리별로 이자 거래 레코드 1건씩 추가.
  - 표준출력(DISPLAY): 시작/종료 메시지, 각 입력 레코드 덤프(`DISPLAY TRAN-CAT-BAL-RECORD` L193), 오류 시 파일 상태.

> 참고: TCATBALF/XREFFILE/DISCGRP는 입력(READ), ACCTFILE은 입출력(REWRITE), TRANSACT는 신규 출력(WRITE)이다.

---

## 의존성

- **COPY (카피북)**:
  - `CVTRA01Y` → `TRAN-CAT-BAL-RECORD` (TCATBALF 레이아웃; `TRANCAT-ACCT-ID`, `TRANCAT-TYPE-CD`, `TRANCAT-CD`, **`TRAN-CAT-BAL`** 포함) (L97)
  - `CVACT03Y` → `CARD-XREF-RECORD` (XREF 레이아웃; **`XREF-CARD-NUM`**) (L102)
  - `CVTRA02Y` → `DIS-GROUP-RECORD` (공시그룹 레이아웃; **`DIS-INT-RATE`**) (L107)
  - `CVACT01Y` → `ACCOUNT-RECORD` (계정 레이아웃; **`ACCT-CURR-BAL`, `ACCT-CURR-CYC-CREDIT`, `ACCT-CURR-CYC-DEBIT`, `ACCT-GROUP-ID`, `ACCT-ID`**) (L112)
  - `CVTRA05Y` → `TRAN-RECORD` (생성할 거래 레이아웃; **`TRAN-ID`, `TRAN-TYPE-CD`, `TRAN-CAT-CD`, `TRAN-SOURCE`, `TRAN-DESC`, `TRAN-AMT`, `TRAN-CARD-NUM`, `TRAN-ORIG-TS`, `TRAN-PROC-TS`** 등) (L117)
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CALL 'CEE3ABD' USING ABCODE, TIMING` (L632) — Language Environment 콜러블 서비스로 **강제 abend**(오류 종료). 비즈니스 서브프로그램 호출은 없음.
- **데이터셋/파일/DB 테이블**:
  - VSAM KSDS: TCATBALF, XREFFILE, DISCGRP, ACCTFILE
  - 순차 파일: TRANSACT (출력)
  - DB2/SQL 직접 접근 **없음** (`EXEC SQL` 없음). 타임스탬프만 DB2 포맷 문자열로 조립.
- **트랜잭션 ID 또는 EXEC PGM**:
  - CICS 트랜잭션 아님(순수 배치). JCL에서 `EXEC PGM=CBACT04C`로 기동. 메모리 기록상 잡명 INTCALC, `app/jcl/INTCALC.jcl` STEP15, `PARM='YYYYMMDD0'`(10바이트).

---

## 핵심 로직 흐름

### 진입 및 OPEN (L180-186)
`PROCEDURE DIVISION USING EXTERNAL-PARMS`로 시작. 5개 파일을 순서대로 OPEN(0000~0400). 각 OPEN 단락은 FILE STATUS를 검사해 `'00'`이 아니면 `9999-ABEND-PROGRAM`으로 강제 종료하는 표준 골격이다(예: L237-249).

### 메인 루프 (L188-222) — control-break의 핵심
```cobol
PERFORM UNTIL END-OF-FILE = 'Y'
    IF  END-OF-FILE = 'N'                              <- (A) 정상 처리 분기
        PERFORM 1000-TCATBALF-GET-NEXT                 <- 다음 카테고리 레코드 READ
        IF  END-OF-FILE = 'N'                          <- 방금 READ가 EOF가 아니면
          ADD 1 TO WS-RECORD-COUNT
          DISPLAY TRAN-CAT-BAL-RECORD
          IF TRANCAT-ACCT-ID NOT= WS-LAST-ACCT-NUM     <- (B) ★ control break 감지
            IF WS-FIRST-TIME NOT = 'Y'
               PERFORM 1050-UPDATE-ACCOUNT             <-   직전 계정 이자 확정·반영
            ELSE
               MOVE 'N' TO WS-FIRST-TIME               <-   최초 1회만 스킵
            END-IF
            MOVE 0 TO WS-TOTAL-INT                     <-   새 계정 누적이자 초기화
            MOVE TRANCAT-ACCT-ID TO WS-LAST-ACCT-NUM   <-   현재 계정키 기억
            MOVE TRANCAT-ACCT-ID TO FD-ACCT-ID
            PERFORM 1100-GET-ACCT-DATA                 <-   새 계정 레코드 READ(I-O)
            MOVE TRANCAT-ACCT-ID TO FD-XREF-ACCT-ID
            PERFORM 1110-GET-XREF-DATA                 <-   새 계정의 카드번호 조회
          END-IF
          MOVE ACCT-GROUP-ID   TO FD-DIS-ACCT-GROUP-ID <- (C) 카테고리마다 매번
          MOVE TRANCAT-CD      TO FD-DIS-TRAN-CAT-CD
          MOVE TRANCAT-TYPE-CD TO FD-DIS-TRAN-TYPE-CD
          PERFORM 1200-GET-INTEREST-RATE               <-   이자율 조회
          IF DIS-INT-RATE NOT = 0
            PERFORM 1300-COMPUTE-INTEREST              <-   이자 계산 + 거래 생성
            PERFORM 1400-COMPUTE-FEES                  <-   (미구현 스텁)
          END-IF
        END-IF
    ELSE                                               <- (D) ★ 마지막 계정 처리
         PERFORM 1050-UPDATE-ACCOUNT
    END-IF
END-PERFORM
```

흐름을 단계로 풀면:

1. **(A) 루프 상단** — `END-OF-FILE='N'`인 동안 다음 레코드를 읽는다. `1000-TCATBALF-GET-NEXT`(L325)는 READ 후 status가 `'10'`(EOF)이면 `END-OF-FILE`을 `'Y'`로 세팅한다(L330,340).

2. **(B) control break 감지** — 현재 레코드의 `TRANCAT-ACCT-ID`가 직전(`WS-LAST-ACCT-NUM`)과 다르면 계정이 바뀐 것이다.
   - 최초 레코드가 아니면(`WS-FIRST-TIME NOT='Y'`) **먼저 직전 계정의 누적 이자를 확정**(`1050-UPDATE-ACCOUNT`). 이 "선처리 후 전환" 순서가 control-break의 정수다.
   - 최초 레코드면 확정할 직전 계정이 없으므로 스킵하고 `WS-FIRST-TIME`만 `'N'`으로.
   - 그 뒤 `WS-TOTAL-INT`를 0으로 리셋하고 새 계정의 마스터(ACCTFILE)와 교차참조(XREF, 카드번호용)를 읽어둔다.

3. **(C) 카테고리별 이자 계산** — 계정 전환 여부와 무관하게 **모든 카테고리 레코드마다** 실행. 계정의 `ACCT-GROUP-ID` + 카테고리의 TYPE/CAT 코드로 DISCGRP에서 이자율을 조회(`1200-GET-INTEREST-RATE`). 이자율이 0이 아니면 `1300-COMPUTE-INTEREST` 수행.

4. **(D) EOF 시 마지막 계정 확정** — ★중요★ 마지막 계정의 카테고리들을 다 읽고 EOF가 되면, 루프는 한 번 더 돌면서 `IF END-OF-FILE='N'`이 거짓이 되어 `ELSE` 가지(L219-220)로 진입해 `1050-UPDATE-ACCOUNT`를 **마지막으로 한 번 더** 호출한다. control-break에서 빠지기 쉬운 "마지막 그룹 미반영" 버그를 이 ELSE 분기가 막는다.

### 이자율 조회 + DEFAULT fallback (L415-460)
`1200-GET-INTEREST-RATE`는 DISCGRP를 RANDOM READ한다. status가 `'23'`(레코드 없음)이면 그룹ID를 `'DEFAULT'`로 바꿔(L437) `1200-A-GET-DEFAULT-INT-RATE`로 재조회한다(L438). 즉 **계정 그룹별 이자율이 없으면 기본 이자율로 폴백**한다. status 검사에서 `'00' OR '23'`를 모두 정상으로 취급(L422)하는 점에 주의.

### 핵심 산술 — 월이자 계산 (L462-470) ★정확성 핵심★
```cobol
1300-COMPUTE-INTEREST.
    COMPUTE WS-MONTHLY-INT
     = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200
    ADD WS-MONTHLY-INT  TO WS-TOTAL-INT
    PERFORM 1300-B-WRITE-TX.
```
- 공식: **월이자 = (카테고리 잔액 × 연이자율%) / 1200**. 여기서 `/1200`은 `/100`(퍼센트→소수)과 `/12`(연→월)를 합친 것이다.
- `DIS-INT-RATE`는 연이자율을 **퍼센트 값**으로 보관(예: 12.50 = 연 12.5%). `CVTRA02Y`상 `PIC S9(4)V99`로 추정(메모리 기록).
- `TRAN-CAT-BAL`, `WS-MONTHLY-INT`, `WS-TOTAL-INT`는 `PIC S9(09)V99`(L168-169) — 소수점 2자리.
- 계산된 월이자를 `WS-TOTAL-INT`에 **누적**(계정 단위 합산)한 뒤, 카테고리별 이자 거래를 즉시 생성(`1300-B-WRITE-TX`).
- ★주의★ COBOL `COMPUTE`의 중간/최종 결과는 `ROUNDED` 절이 없으면 **기본적으로 truncate(절사)**된다. Java 이식 시 반드시 동일한 절사 동작을 재현해야 1원 단위 차이를 막는다(아래 노트 참조).

### 이자 거래 레코드 생성 (L473-515)
`1300-B-WRITE-TX`가 `TRAN-RECORD`를 채워 TRANSACT에 WRITE:
- `TRAN-ID` = `PARM-DATE`(처리일자 10자리) + `WS-TRANID-SUFFIX`(6자리 시퀀스, 매 거래 +1) 를 `STRING ... DELIMITED BY SIZE`로 연결(L474-480).
- `TRAN-TYPE-CD='01'`, `TRAN-CAT-CD='05'`(이자 거래 유형/카테고리 고정), `TRAN-SOURCE='System'` (L482-484).
- `TRAN-DESC` = `'Int. for a/c ' + ACCT-ID` (L485-489).
- `TRAN-AMT` = `WS-MONTHLY-INT`(해당 카테고리 이자), 가맹점 필드는 0/공백, `TRAN-CARD-NUM` = XREF에서 얻은 `XREF-CARD-NUM`(L490-495).
- `TRAN-ORIG-TS`/`TRAN-PROC-TS` = `Z-GET-DB2-FORMAT-TIMESTAMP`(L613)가 `FUNCTION CURRENT-DATE`를 DB2 타임스탬프 포맷 `YYYY-MM-DD-HH.MM.SS.mmm0000`로 조립한 값(L496-498).

### 계정 잔액 반영 (L350-370)
`1050-UPDATE-ACCOUNT`는 control break 시(또는 EOF 시) 호출되어:
- `ADD WS-TOTAL-INT TO ACCT-CURR-BAL` — **누적 이자를 계정 현재 잔액에 가산**(L352).
- `ACCT-CURR-CYC-CREDIT`, `ACCT-CURR-CYC-DEBIT`를 **0으로 리셋**(L353-354) — 사이클 차/대변 누계를 새 청구주기용으로 초기화.
- `REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD`(L356)로 계정 마스터 갱신.

### CLOSE 및 종료 (L224-232)
5개 파일을 CLOSE(9000~9400) 후 `GOBACK`(L232)으로 호출자(JCL 스텝)에 복귀.

### 오류 처리
모든 I/O 단락은 동일 패턴: status 검사 → `9910-DISPLAY-IO-STATUS`(L635, 비숫자/`'9'` 시작 status도 보기 좋게 표시) → `9999-ABEND-PROGRAM`(L628)에서 `CALL 'CEE3ABD'`로 ABEND 코드 999로 강제 종료. `1100-GET-ACCT-DATA`/`1110-GET-XREF-DATA`/`1200-GET-INTEREST-RATE`는 `INVALID KEY` 시 메시지만 DISPLAY하지만(L375,397,418), 직후 status 검사에서 `'00'`이 아니면 결국 abend로 이어진다.

---

## Java/현대화 노트

### 1. 금액·이자 산술은 반드시 `BigDecimal` + 명시적 RoundingMode
`PIC S9(09)V99`는 부호 있는 정수 9자리 + 소수 2자리(고정소수점)다. `double`/`float`로 옮기면 누적 오차가 발생하므로 **`BigDecimal scale=2`**가 정답이다.

```java
// COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
BigDecimal monthlyInt = tranCatBal
        .multiply(disIntRate)               // 잔액 × 연이자율%
        .divide(new BigDecimal("1200"), 2,  // /100/12, scale 2
                RoundingMode.DOWN);         // ★ COBOL COMPUTE 기본 = 절사(truncate)
totalInt = totalInt.add(monthlyInt);
```
- ★핵심★ COBOL `COMPUTE`에 `ROUNDED`가 없으면 결과는 **버림(truncate)**이다. Java `divide`의 기본 동작이 아니므로 `RoundingMode.DOWN`을 **명시**해야 레거시와 1:1로 일치한다. (HALF_UP을 쓰면 미세하게 어긋남.)
- `DIS-INT-RATE`가 퍼센트 값(예 12.50)이라는 도메인 의미를 타입/주석으로 남길 것.

### 2. control-break = group-by 집계로 직역
정렬된 입력을 전제로 한 control-break는 Java에서 두 가지로 표현 가능하다.
```java
// 방식 A: 명령형 — COBOL 구조와 1:1 (이식 검증이 쉬움)
String lastAcct = null;
BigDecimal totalInt = BigDecimal.ZERO;
Account acct = null;
for (TranCatBal row : tcatbalReader) {        // 계정ID 오름차순 정렬 전제
    if (!row.acctId().equals(lastAcct)) {
        if (lastAcct != null) updateAccount(acct, totalInt);  // ← 직전 계정 flush
        totalInt = BigDecimal.ZERO;
        lastAcct = row.acctId();
        acct = accountRepo.read(row.acctId());
        // ... XREF로 카드번호 조회
    }
    BigDecimal rate = lookupRate(acct.groupId(), row.typeCd(), row.catCd());
    if (rate.signum() != 0) {
        BigDecimal mInt = compute(row.balance(), rate);
        totalInt = totalInt.add(mInt);
        writeInterestTx(...);
    }
}
if (lastAcct != null) updateAccount(acct, totalInt);  // ★ 루프 후 마지막 계정 flush
```
- ★주의★ COBOL의 마지막 그룹은 **EOF 루프의 ELSE 분기**(L219-220)로 처리된다. Java로 옮길 때 이 "**루프 종료 후 마지막 그룹 flush**"를 빠뜨리면 마지막 계정 이자가 누락된다 — 가장 흔한 이식 버그.
- Spring Batch라면 TCATBALF=`ItemReader`, control-break 집계는 커스텀 `ItemProcessor`/`AggregateItemReader` 혹은 계정 단위 청크 경계, 이자 거래=`ItemWriter`, ACCTFILE 갱신=별도 writer로 매핑(메모리 기록과 동일).

### 3. `WS-FIRST-TIME` 플래그 → `lastAcct == null` 가드
COBOL은 native null이 없어 `WS-FIRST-TIME` 플래그로 "아직 직전 계정 없음"을 표현한다(L170,195). Java에서는 `lastAcct == null` 한 줄로 대체된다 — 별도 boolean 불필요.

### 4. 파일 접근을 Repository로 추상화
- TCATBALF(SEQUENTIAL) → 정렬된 커서/스트림 reader.
- XREFFILE 대체키 조회(L394-398, `READ ... KEY IS FD-XREF-ACCT-ID`) → `findByAcctId()`. VSAM 대체 인덱스 = 보조 인덱스 컬럼 조회.
- ACCTFILE I-O의 READ→REWRITE → `accountRepo.findById()` 후 `save()`(낙관적 락 고려).
- DISCGRP RANDOM READ + DEFAULT 폴백 → `findRate(group,type,cat).orElseGet(() -> findRate("DEFAULT",type,cat))` 패턴이 자연스럽다(L436-439).

### 5. DB2 타임스탬프 문자열 조립 → `LocalDateTime`/`Timestamp`
`Z-GET-DB2-FORMAT-TIMESTAMP`(L613)는 `FUNCTION CURRENT-DATE`를 받아 `REDEFINES`로 자리수를 잘라 `YYYY-MM-DD-HH.MM.SS.mmm0000` 형태 문자열을 만든다. Java에서는 `LocalDateTime.now()` + `DateTimeFormatter`(또는 JDBC `Timestamp`)로 대체한다. 다만 출력 포맷의 마지막 `0000` 자리(마이크로초 패딩) 등 **자릿수 호환**이 필요하면 포맷 문자열을 맞춰야 한다.

### 6. `STRING ... DELIMITED BY SIZE`로 만든 키 → 문자열 결합 + zero-fill
- `TRAN-ID` = 처리일자 + 6자리 시퀀스(L474-480). `WS-TRANID-SUFFIX PIC 9(06)`은 zero-padding되므로 Java에서 `String.format("%s%06d", parmDate, suffix)` 식으로 재현. ★주의★ 시퀀스는 **프로그램 전체에서 단조 증가**(계정마다 리셋 아님)이며, 잡을 다시 돌리면 같은 PARM-DATE로 동일 ID가 재생성될 수 있어 멱등성/중복 처리에 유의.

### 7. 직접 Java 대응이 없는 mainframe 요소
- **`REDEFINES`**(L126,151) — `DB2-FORMAT-TS`와 `TWO-BYTES-BINARY`를 동일 메모리에 다른 뷰로 겹쳐 본다. Java에 직접 등가물이 없으므로 "바이트 버퍼를 두 방식으로 파싱"하는 헬퍼로 풀어야 한다. 타임스탬프 조립은 차라리 문자열/날짜 API로 재작성 권장.
- **고정장 레코드 / READ INTO** — `READ ... INTO`(L326 등)는 레코드 영역을 WORKING-STORAGE 복사본으로 옮긴다. Java에서는 고정폭 레이아웃 파서(또는 copybook 기반 매퍼)로 DTO에 매핑.
- **FILE STATUS 2바이트 코드** — `'00'`(정상)/`'10'`(EOF)/`'23'`(레코드 없음) 등은 Java의 정상 리턴/`Optional.empty()`/예외로 사상. `'23'`을 "정상으로 간주 후 폴백"하는 의미를 놓치지 말 것(L422).
- **`CALL 'CEE3ABD'`로 ABEND**(L632) — JVM에는 등가 개념이 없다. 미복구 예외를 던져 잡을 비정상 종료시키고, 잡 스케줄러가 RC로 감지하도록 매핑.

### 8. 미구현 스텁
`1400-COMPUTE-FEES`(L518-520)는 `EXIT`만 있는 빈 단락이다("To be implemented"). 수수료 계산은 미구현이며, Java 이식 시 hook(빈 메서드/전략 인터페이스)으로 남겨 향후 확장 지점으로 둘 수 있다.

### 불확실 항목
- DISCGRP의 정확한 `DIS-INT-RATE` PIC와 ACCTFILE 잔액 필드들의 정밀도는 직접 COPY한 `CVTRA02Y`/`CVACT01Y` 원본을 확인해야 100% 확정된다. 위 `S9(4)V99`/`S9(09)V99` 추정은 본 프로그램의 작업변수 정의(L168-169)와 메모리 기록에 근거한 것이다. **(추측)** — 1원 단위 정밀도가 중요한 산술이므로, 이식 전 두 copybook의 PIC 절을 반드시 대조할 것.
