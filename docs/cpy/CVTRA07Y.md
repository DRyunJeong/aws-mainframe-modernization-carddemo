# CVTRA07Y — 일일 거래 리포트 출력 레이아웃

- **유형**: Copybook
- **한 줄 요약**: 배치 리포트 프로그램 CBTRN03C가 프린터 출력 파일(TRANREPT, 133자 고정폭)에 쓰는 모든 줄 유형—타이틀 헤더, 컬럼 헤더, 거래 상세 한 줄, 페이지 소계, 계정 소계, 그랜드 합계—의 레이아웃을 정의하는 리포트-레이아웃 전용 copybook이다.

---

## 기능 설명

CVTRA07Y는 CardDemo의 **일일 거래 상세 리포트**(Daily Transaction Report)를 구성하는 인쇄 레코드 구조 전체를 한 파일에 모아 둔 copybook이다. COBOL에서 리포트 한 줄 한 줄은 각기 다른 01 레벨 구조체로 선언하고, 프로그램은 해당 구조체를 채운 뒤 `WRITE FD-REPTFILE-REC FROM <01-구조체>`로 출력 파일에 쓴다.

이 copybook이 정의하는 6개의 01 레벨 구조체는 세 가지 역할로 나뉜다.

1. **타이틀 구역** (`REPORT-NAME-HEADER`): 리포트 제목("Daily Transaction Report")과 날짜 범위(`REPT-START-DATE` / `REPT-END-DATE`)를 담는다. CBTRN03C는 리포트 시작 시 이 구조체를 1회 출력한다.
2. **컬럼 헤더 구역** (`TRANSACTION-HEADER-1`, `TRANSACTION-HEADER-2`): 컬럼 이름 행과 `---` 구분선(133자 `ALL '-'`)으로 구성된다. 페이지 넘김 시 매번 재출력된다.
3. **상세 및 집계 구역** (`TRANSACTION-DETAIL-REPORT`, `REPORT-PAGE-TOTALS`, `REPORT-ACCOUNT-TOTALS`, `REPORT-GRAND-TOTALS`): 거래 1건을 한 줄로 표현하는 상세 레코드와 세 종류의 집계 행이다.

Java/Spring Batch 관점에서는 이 6개 구조체가 `ItemWriter`가 출력하는 **여러 종류의 `String` 포맷터(PrintLine) 클래스** 역할을 한다. COBOL이 고정폭 PIC 편집 마스크로 숫자를 직접 포맷하는 반면, Java에서는 `String.format()` 또는 `DecimalFormat`으로 동등한 출력을 생성한다.

출력 파일의 고정폭은 133자(`PIC X(133)`, CVTRA07Y 라인 48)이며, 이는 IBM 라인프린터의 표준 폭이다(첫 번째 바이트가 ASA 제어 문자인 관행과 별개로, 이 파일에서 ASA 제어 문자 사용 여부는 CBTRN03C FD 정의를 별도 확인 필요).

---

## 필드 레이아웃

### 01 REPORT-NAME-HEADER (라인 4~13)

타이틀 행 단 한 줄로 구성된다. 각 하위 필드는 `VALUE` 초기값을 가지며, 날짜 범위 필드만 CBTRN03C가 실행 시 덮어쓴다.

| 필드명 | 오프셋(1~) | PIC / USAGE | 바이트 | 의미 및 Java 대응 |
|---|---|---|---|---|
| `REPT-SHORT-NAME` | 1 | `PIC X(38)` VALUE `'DALYREPT'` | 38 | 리포트 단축명 식별자. 실제 출력 시 "DALYREPT" 뒤 30자는 SPACE. Java: `String` 상수 `"DALYREPT"`. |
| `REPT-LONG-NAME` | 39 | `PIC X(41)` VALUE `'Daily Transaction Report'` | 41 | 리포트 전체 이름. 출력 헤더 타이틀 문자열. Java: `String` 상수. |
| `REPT-DATE-HEADER` | 80 | `PIC X(12)` VALUE `'Date Range: '` | 12 | 날짜 범위 레이블 고정 문자열. |
| `REPT-START-DATE` | 92 | `PIC X(10)` VALUE SPACES | 10 | 리포트 시작 일자. CBTRN03C가 DATEPARM 파일에서 읽은 `WS-START-DATE`(X(10))를 MOVE한다. 형식: `yyyy-MM-dd`(ISO 문자열, 추측). Java: `LocalDate.parse(...)` 후 `DateTimeFormatter.ISO_LOCAL_DATE`로 재포맷. |
| `FILLER` | 102 | `PIC X(04)` VALUE `' to '` | 4 | 날짜 구분 문자열 고정값. |
| `REPT-END-DATE` | 106 | `PIC X(10)` VALUE SPACES | 10 | 리포트 종료 일자. `WS-END-DATE`(X(10))를 MOVE. |

총 116바이트. 리포트 파일 레코드(133바이트)보다 짧으므로 WRITE 시 나머지 17바이트는 SPACE 패딩된다(추측).

---

### 01 TRANSACTION-DETAIL-REPORT (라인 15~31)

거래 1건을 한 줄로 표현하는 핵심 상세 레코드이다. CBTRN03C는 거래 1건을 처리할 때마다 각 하위 필드에 값을 MOVE한 뒤 이 구조체를 출력 파일에 WRITE한다.

| 필드명 | 오프셋(1~) | PIC / USAGE | 바이트 | 의미 및 Java 대응 |
|---|---|---|---|---|
| `TRAN-REPORT-TRANS-ID` | 1 | `PIC X(16)` | 16 | 거래 고유 ID. `CVTRA05Y`의 `TRAN-ID`(X(16))에서 MOVE. Java: `String` (16자 고정). |
| `FILLER` | 17 | `PIC X(01)` VALUE SPACES | 1 | 컬럼 구분 공백. |
| `TRAN-REPORT-ACCOUNT-ID` | 18 | `PIC X(11)` | 11 | 계정 ID. `CVACT01Y`의 `ACCT-ID`(9(11))를 MOVE 또는 CARDXREF 룩업 결과. Java: `String`. |
| `FILLER` | 29 | `PIC X(01)` VALUE SPACES | 1 | 컬럼 구분 공백. |
| `TRAN-REPORT-TYPE-CD` | 30 | `PIC X(02)` | 2 | 거래 유형 코드(예: "PU", "SA"). `CVTRA05Y`의 `TRAN-TYPE-CD`(X(02)). Java: `String` (2자). |
| `FILLER` | 32 | `PIC X(01)` VALUE `'-'` | 1 | 코드-설명 구분 하이픈. |
| `TRAN-REPORT-TYPE-DESC` | 33 | `PIC X(15)` | 15 | 거래 유형 설명(TRANTYPE KSDS에서 룩업). `CVTRA03Y`의 `TRAN-TYPE-DESC`(X(50)) 앞 15자. Java: `String`, 50자 원본을 15자로 잘라 오른쪽 공백 제거 필요. |
| `FILLER` | 48 | `PIC X(01)` VALUE SPACES | 1 | 컬럼 구분 공백. |
| `TRAN-REPORT-CAT-CD` | 49 | `PIC 9(04)` | 4 | 거래 카테고리 코드(숫자 4자리). `CVTRA05Y`의 `TRAN-CAT-CD`(9(04)). Java: `int`. |
| `FILLER` | 53 | `PIC X(01)` VALUE `'-'` | 1 | 코드-설명 구분 하이픈. |
| `TRAN-REPORT-CAT-DESC` | 54 | `PIC X(29)` | 29 | 거래 카테고리 설명(TRANCATG KSDS에서 룩업). `CVTRA04Y`의 카테고리 설명 필드. Java: `String`, 오른쪽 공백 trim 필요. |
| `FILLER` | 83 | `PIC X(01)` VALUE SPACES | 1 | 컬럼 구분 공백. |
| `TRAN-REPORT-SOURCE` | 84 | `PIC X(10)` | 10 | 거래 발생 소스(채널). `CVTRA05Y`의 `TRAN-ORIG-TS` 또는 별도 소스 필드(추측, CBTRN03C 소스 확인 필요). Java: `String`. |
| `FILLER` | 94 | `PIC X(04)` VALUE SPACES | 4 | 오른쪽 여백. |
| `TRAN-REPORT-AMT` | 98 | `PIC -ZZZ,ZZZ,ZZZ.ZZ` | 14 | **편집 그림(Edit Picture)**: 음수이면 선행 `-`, 양수이면 선행 공백, 선행 0은 SPACE로 치환(Z 편집). 소수점 포함 금액. Java 대응: `BigDecimal` 원본을 `String.format("% ,14.2f", amount)` 또는 `DecimalFormat`으로 포맷. 이 필드는 DISPLAY 편집 필드이므로 산술 연산에 직접 사용할 수 없다. |
| `FILLER` | 112 | `PIC X(02)` VALUE SPACES | 2 | 우측 마진. |

총 약 113바이트(FILLER 포함). 133바이트 레코드에서 나머지 20바이트는 패딩(추측).

> **주의**: `PIC -ZZZ,ZZZ,ZZZ.ZZ`는 부호, 0 억제(Z), 쉼표, 소수점을 포함하는 편집 PIC이다. 실제 저장 바이트는 표시 문자 수와 동일하다(`-`, `Z`, `,`, `.`는 모두 1바이트). COMP-3/COMP와 달리 이 편집 필드 자체는 사람이 읽을 수 있는 텍스트로 저장된다.

---

### 01 TRANSACTION-HEADER-1 (라인 33~46)

컬럼명 행. 모든 하위 필드가 `VALUE` 고정값 FILLER이므로 이 구조체를 WRITE하면 항상 동일한 헤더 줄이 출력된다.

| 필드명 | PIC | 바이트 | 고정 내용 |
|---|---|---|---|
| `FILLER` (×6) | `PIC X(n)` | 17, 12, 19, 35, 14, 1 | "Transaction ID", "Account ID", "Transaction Type", "Tran Category", "Tran Source", SPACE |
| `FILLER` | `PIC X(16)` | 16 | `'        Amount'` (8 SPACE + "Amount") |

총 114바이트(17+12+19+35+14+1+16 = 114). 133바이트 레코드에서 나머지 19바이트는 SPACE 패딩.

Java에서는 이 행 전체를 상수 `String` 1개로 대체할 수 있다.

---

### 01 TRANSACTION-HEADER-2 (라인 48)

```
01  TRANSACTION-HEADER-2  PIC X(133) VALUE ALL '-'.
```

133자 전체가 `-`로 채워진 구분선 레코드이다. 하위 필드가 없는 단일 레벨 구조체. Java 대응: `"-".repeat(133)`.

---

### 01 REPORT-PAGE-TOTALS (라인 50~54)

페이지 단위 합계 행이다. 20줄(WS-PAGE-SIZE)마다 출력된다.

| 필드명 | 오프셋(1~) | PIC / USAGE | 바이트 | 의미 및 Java 대응 |
|---|---|---|---|---|
| `FILLER` | 1 | `PIC X(11)` VALUE `'Page Total'` | 11 | 레이블 고정 문자열. |
| `FILLER` | 12 | `PIC X(86)` VALUE ALL `'.'` | 86 | 레이블과 합계 사이 점선 패딩. Java: `".".repeat(86)`. |
| `REPT-PAGE-TOTAL` | 98 | `PIC +ZZZ,ZZZ,ZZZ.ZZ` | 14 | **편집 그림**: 양수이면 `+`, 음수이면 `-` 부호가 항상 출력된다는 점에서 `PIC -...` 와 다르다. CBTRN03C의 `WS-PAGE-TOTAL`(S9(09)V99)을 MOVE. Java: `BigDecimal` → `String.format("%+,.2f", ...)` 또는 `new DecimalFormat("+#,##0.00;-#,##0.00")`. |

총 111바이트(11+86+14). 나머지 22바이트 패딩(추측).

---

### 01 REPORT-ACCOUNT-TOTALS (라인 56~60)

계정(카드) 단위 control-break 합계 행. 카드 번호가 바뀔 때마다 출력된다.

| 필드명 | 오프셋(1~) | PIC / USAGE | 바이트 | 의미 및 Java 대응 |
|---|---|---|---|---|
| `FILLER` | 1 | `PIC X(13)` VALUE `'Account Total'` | 13 | 레이블 고정 문자열. |
| `FILLER` | 14 | `PIC X(84)` VALUE ALL `'.'` | 84 | 점선 패딩. Java: `".".repeat(84)`. |
| `REPT-ACCOUNT-TOTAL` | 98 | `PIC +ZZZ,ZZZ,ZZZ.ZZ` | 14 | 계정 합계 금액 편집값. CBTRN03C의 `WS-ACCOUNT-TOTAL`(S9(09)V99)을 MOVE. Java: `BigDecimal`. |

총 111바이트. `REPT-PAGE-TOTAL`과 동일한 오프셋 98에서 금액 시작 — 리포트에서 금액 컬럼이 정렬됨을 알 수 있다.

---

### 01 REPORT-GRAND-TOTALS (라인 62~66)

리포트 전체의 그랜드 합계 행. 리포트 끝에 1회 출력된다.

| 필드명 | 오프셋(1~) | PIC / USAGE | 바이트 | 의미 및 Java 대응 |
|---|---|---|---|---|
| `FILLER` | 1 | `PIC X(11)` VALUE `'Grand Total'` | 11 | 레이블 고정 문자열. |
| `FILLER` | 12 | `PIC X(86)` VALUE ALL `'.'` | 86 | 점선 패딩. |
| `REPT-GRAND-TOTAL` | 98 | `PIC +ZZZ,ZZZ,ZZZ.ZZ` | 14 | 전체 합계 금액. CBTRN03C의 `WS-GRAND-TOTAL`(S9(09)V99)을 MOVE. Java: `BigDecimal`. |

총 111바이트. 세 집계 행 모두 동일한 레이아웃(`레이블 + 점선 FILLER + 금액`)을 따른다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**:
  - `TRANREPT` (DD명) — 순차 출력 파일. CBTRN03C의 FD `FD-REPTFILE-REC PIC X(133)`에 대응. 이 copybook의 모든 01 구조체가 이 파일에 WRITE된다. 파일 형식: QSAM, RECFM=FB(추측), LRECL=133.
  - `TRANSACT` (DD명) — CBTRN03C의 드라이빙 입력 파일. `CVTRA05Y`(`TRAN-RECORD`) 레이아웃. `TRAN-REPORT-TRANS-ID`, `TRAN-REPORT-TYPE-CD` 등 상세 레코드 필드의 원본 소스.
  - `DATEPARM` (DD명) — 날짜 범위 파라미터 파일. `REPT-START-DATE` / `REPT-END-DATE`의 원본 소스.
  - `CARDXREF` (DD명) — VSAM KSDS. `CVACT03Y`(`CARD-XREF-RECORD`) 레이아웃. `TRAN-REPORT-ACCOUNT-ID` 조회에 사용.
  - `TRANTYPE` (DD명) — VSAM KSDS. `CVTRA03Y`(`TRAN-TYPE-RECORD`) 레이아웃. `TRAN-REPORT-TYPE-DESC` 조회에 사용.
  - `TRANCATG` (DD명) — VSAM KSDS. `CVTRA04Y` 레이아웃. `TRAN-REPORT-CAT-DESC` 조회에 사용.
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. 리포트 레이아웃 → Java 리포트 라인 클래스

COBOL에서는 01 레벨 구조체 6개가 각각 독립적인 고정폭 행 유형을 표현한다. Java에서는 Sealed Interface 또는 단순 포맷 유틸리티로 동등하게 표현할 수 있다.

```java
// 6가지 출력 행 유형을 sealed interface로 모델링
public sealed interface ReportLine permits
    ReportNameHeader,
    TransactionDetailLine,
    TransactionColumnHeader,
    TransactionColumnDivider,
    PageTotalLine,
    AccountTotalLine,
    GrandTotalLine {
    String format(); // 133자 고정폭 String 반환
}

// 상세 행 예시
public record TransactionDetailLine(
    String transId,         // TRAN-REPORT-TRANS-ID  X(16)
    String accountId,       // TRAN-REPORT-ACCOUNT-ID X(11)
    String typeCode,        // TRAN-REPORT-TYPE-CD    X(02)
    String typeDesc,        // TRAN-REPORT-TYPE-DESC  X(15) — trim 후 15자
    int    catCode,         // TRAN-REPORT-CAT-CD     9(04)
    String catDesc,         // TRAN-REPORT-CAT-DESC   X(29)
    String source,          // TRAN-REPORT-SOURCE     X(10)
    BigDecimal amount       // TRAN-REPORT-AMT 원본 (편집 전)
) implements ReportLine {
    @Override
    public String format() {
        return String.format("%-16s %-11s %-2s-%-15s %04d-%-29s %-10s    %14s  ",
            transId, accountId, typeCode, typeDesc,
            catCode, catDesc, source,
            formatAmount(amount));
    }

    private String formatAmount(BigDecimal v) {
        // PIC -ZZZ,ZZZ,ZZZ.ZZ: 음수 선행 '-', 양수 선행 ' ', 선행 0→SPACE
        DecimalFormat df = new DecimalFormat(" #,##0.00;-#,##0.00");
        return String.format("%14s", df.format(v));
    }
}
```

### 2. 편집 PIC 해독 — `-ZZZ,ZZZ,ZZZ.ZZ` 대 `+ZZZ,ZZZ,ZZZ.ZZ`

이 copybook에는 두 가지 숫자 편집 마스크가 혼용된다.

| 마스크 | 부호 출력 방식 | COBOL 용도 | Java DecimalFormat 패턴 |
|---|---|---|---|
| `PIC -ZZZ,ZZZ,ZZZ.ZZ` | 음수: `'-'`, 양수: `' '`(공백) | 상세 행 금액 | `" #,##0.00;-#,##0.00"` |
| `PIC +ZZZ,ZZZ,ZZZ.ZZ` | 음수: `'-'`, 양수: `'+'` | 집계 행 금액 | `"+#,##0.00;-#,##0.00"` |

`Z` 편집 문자는 선행 0을 SPACE로 치환한다. 금액이 0인 경우 `ZZZ,ZZZ,ZZZ.ZZ`는 `         .00`으로 출력되지 않고 `          0.00` 또는 완전히 공백(구현에 따라 다름)이 될 수 있다 — Java 마이그레이션 시 0값 처리를 회귀 검증해야 한다.

집계 합산의 원본 WORKING-STORAGE 변수는 `S9(09)V99`(DISPLAY)이며, Java에서는 `BigDecimal(scale=2)`로 대응한다. `double`/`float`는 십진 소수 표현 오차로 인해 금융 금액에 절대 사용하지 않는다.

### 3. control-break 집계 패턴 → Spring Batch ItemWriter

CBTRN03C의 집계 흐름은 다음 세 누산 변수로 동작한다(CBTRN03C WORKING-STORAGE 기준).

```
WS-PAGE-TOTAL    S9(09)V99  ← 20줄마다 출력 후 0으로 리셋
WS-ACCOUNT-TOTAL S9(09)V99  ← 카드 번호 변경 시 출력 후 0으로 리셋
WS-GRAND-TOTAL   S9(09)V99  ← 리포트 종료 시 1회 출력
```

Spring Batch로 전환할 경우 `ItemWriteListener` 또는 커스텀 `ItemWriter`에서 동일한 세 변수를 `BigDecimal`로 유지하면서 flush 조건(20줄, 카드 변경, EOF)을 관리하는 패턴을 권장한다.

```java
@Component
public class TransactionReportWriter implements ItemWriter<TransactionDetailLine> {
    private BigDecimal pageTotal    = BigDecimal.ZERO;
    private BigDecimal accountTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal   = BigDecimal.ZERO;
    private String     lastCardNum  = null;
    private int        lineCount    = 0;
    private static final int PAGE_SIZE = 20; // WS-PAGE-SIZE

    @Override
    public void write(Chunk<? extends TransactionDetailLine> items) throws Exception {
        for (TransactionDetailLine line : items) {
            if (!line.accountId().equals(lastCardNum)) {
                if (lastCardNum != null) {
                    flushAccountTotal();
                }
                lastCardNum = line.accountId();
                accountTotal = BigDecimal.ZERO;
            }
            // 상세 행 출력
            writeLine(line.format());
            pageTotal    = pageTotal.add(line.amount());
            accountTotal = accountTotal.add(line.amount());
            grandTotal   = grandTotal.add(line.amount());
            lineCount++;
            if (lineCount % PAGE_SIZE == 0) {
                flushPageTotal();
            }
        }
    }

    // afterJob 시점에 flushAccountTotal() + flushGrandTotal() 호출
}
```

> **이식 주의**: CBTRN03C 메모리([[transaction-report-cbtrn03c]])에 기록된 대로, 원본 COBOL의 EOF 분기에서 **마지막 계정 소계 누락 및 마지막 거래 금액 중복 가산 가능성**이 있다. Java 재구현 시 EOF 이후 잔여 집계 flush 처리를 반드시 명시적으로 코딩하고, 원본 COBOL 출력과 바이트 단위로 비교하는 회귀 테스트를 수행해야 한다.

### 4. 고정폭 133바이트 레코드 처리

IBM 라인프린터 관행상 133바이트 레코드의 첫 번째 바이트는 **ASA 제어 문자**(공백=새 줄, `1`=페이지 넘김, `0`=2줄 건너뜀 등)로 예약되는 경우가 있다. CBTRN03C FD의 `RECORDING MODE` 또는 JCL의 `RECFM=FBA` 여부를 확인하지 않아 이 파일에 ASA 제어 문자가 사용되는지는 불확실하다(추측). Java에서 이 파일을 직접 파싱할 경우 첫 바이트 처리 방식을 반드시 JCL/FD에서 확인해야 한다.

### 5. EBCDIC 변환 고려

메인프레임에서 생성된 TRANREPT 파일을 Java로 읽을 경우 EBCDIC(`Cp1047` 또는 `IBM037`) → UTF-8 변환이 필요하다. 특히 하이픈(`-`), 점(`.`), 쉼표(`,`)의 EBCDIC 코드 포인트가 ASCII와 다르므로 변환 없이 읽으면 금액 편집 결과가 깨진다.

```java
// EBCDIC 파일 읽기 예시
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
            new FileInputStream("TRANREPT"), "Cp1047"))) {
    String line;
    while ((line = reader.readLine()) != null) {
        // 각 행은 133자 고정폭
        parseLine(line);
    }
}
```

### 6. 상세 레코드와 참조 copybook 연결 요약

`TRANSACTION-DETAIL-REPORT`의 각 필드가 어느 copybook/파일에서 조달되는지 일목요연하게 정리하면 다음과 같다.

| 상세 필드 | 원본 copybook | 원본 파일 |
|---|---|---|
| `TRAN-REPORT-TRANS-ID` | `CVTRA05Y` (`TRAN-ID`) | `TRANSACT` (KSDS) |
| `TRAN-REPORT-ACCOUNT-ID` | `CVACT03Y` (`XREF-ACCT-ID`) | `CARDXREF` (KSDS) |
| `TRAN-REPORT-TYPE-CD` | `CVTRA05Y` (`TRAN-TYPE-CD`) | `TRANSACT` (KSDS) |
| `TRAN-REPORT-TYPE-DESC` | `CVTRA03Y` (`TRAN-TYPE-DESC`) | `TRANTYPE` (KSDS) |
| `TRAN-REPORT-CAT-CD` | `CVTRA05Y` (`TRAN-CAT-CD`) | `TRANSACT` (KSDS) |
| `TRAN-REPORT-CAT-DESC` | `CVTRA04Y` (카테고리 설명 필드) | `TRANCATG` (KSDS) |
| `TRAN-REPORT-SOURCE` | `CVTRA05Y` (소스 관련 필드, 추측) | `TRANSACT` (KSDS) |
| `TRAN-REPORT-AMT` | `CVTRA05Y` (`TRAN-AMT`) | `TRANSACT` (KSDS) |

> 버전 정보: `CardDemo_v1.0-15-g27d6c6f-68`, 2022-07-19 (CVTRA07Y 라인 72).
