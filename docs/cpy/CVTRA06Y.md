# CVTRA06Y — 일일 거래 레코드 (DALYTRANsaction Record)

- **유형**: Copybook
- **한 줄 요약**: 야간 배치 처리에서 읽어 들이는 일일 거래(DALYTRAN) 레코드의 350바이트 QSAM 고정-길이 레이아웃을 정의한다. CVTRA05Y(`TRAN-RECORD`)와 필드 구조·길이가 완전히 동일하며, 접두사만 `TRAN-` → `DALYTRAN-`으로 다르다.

---

## 기능 설명

`CVTRA06Y`는 CardDemo 야간 배치 파이프라인의 **입력 파일 레코드 스키마**다.
카드사가 하루 동안 수집한 거래 데이터를 QSAM 순차 파일(`AWS.M2.CARDDEMO.DALYTRAN.PS`, RECFM=FB, LRECL=350)에 담아두면, 배치 프로그램이 이 copybook으로 레코드를 파싱해 처리한다.

처리 흐름상 위치:

```
외부 거래 파일(DALYTRAN.PS) ──CBTRN01C──▶ 유효성 검사
                                          └──▶ CBTRN02C ──▶ TRANSACT KSDS(CVTRA05Y) 적재
```

- **CBTRN01C**: DALYTRAN 파일을 순차 읽기(`OPEN INPUT`)하여 기본 유효성 검사를 수행한다.
- **CBTRN02C**: 검증된 레코드를 받아 계정·카드 VSAM을 룩업한 뒤 TRANSACT KSDS에 저장한다. 저장 시 `TRAN-RECORD`(CVTRA05Y)로 매핑되므로, DALYTRAN 레코드는 사실상 TRAN-RECORD의 배치-입력 미러(mirror)다.

CVTRA05Y(`TRAN-RECORD`)와의 관계: **구조·바이트 위치·길이가 100% 동일**하고 필드 접두사만 다르다. 두 copybook이 분리된 이유는 파일 출처(배치 입력 vs VSAM 저장소)와 변수명 충돌 방지를 위해서다.

---

## 필드 레이아웃

레코드 총 길이: **350바이트** (라인 2 주석 "RECLN = 350")

| 필드명 | PIC / USAGE | 바이트 수 | 의미 |
|---|---|---|---|
| `DALYTRAN-ID` | `PIC X(16)` | 16 | 거래 고유 식별자. 문자열 키. |
| `DALYTRAN-TYPE-CD` | `PIC X(02)` | 2 | 거래 유형 코드 (예: `DB`=직불, `CR`=신용). CVTRA03Y 참조 테이블과 조인. |
| `DALYTRAN-CAT-CD` | `PIC 9(04)` | 4 | 거래 카테고리 코드. DISPLAY 숫자(문자 4자리). CVTRA04Y 참조 테이블과 조인. |
| `DALYTRAN-SOURCE` | `PIC X(10)` | 10 | 거래 발원 채널 (예: `POS`, `ATM`, `ONLINE` 등). |
| `DALYTRAN-DESC` | `PIC X(100)` | 100 | 거래 설명 자유 텍스트. 공백 패딩(fixed-length). |
| `DALYTRAN-AMT` | `PIC S9(09)V99` | 11 | 거래 금액. 부호 포함 DISPLAY 숫자. V는 **묵시적 소수점**(저장 시 소수점 문자 없음). 정수 9자리 + 소수 2자리 = 총 11자리 문자. |
| `DALYTRAN-MERCHANT-ID` | `PIC 9(09)` | 9 | 가맹점 ID. 부호 없는 DISPLAY 숫자. |
| `DALYTRAN-MERCHANT-NAME` | `PIC X(50)` | 50 | 가맹점 상호. 공백 패딩. |
| `DALYTRAN-MERCHANT-CITY` | `PIC X(50)` | 50 | 가맹점 소재 도시. |
| `DALYTRAN-MERCHANT-ZIP` | `PIC X(10)` | 10 | 가맹점 우편번호. 영문권 ZIP/포스탈코드 혼용 가능하므로 X(영숫자). |
| `DALYTRAN-CARD-NUM` | `PIC X(16)` | 16 | 카드 번호. CVACT02Y(`CARD-RECORD`)의 카드 번호와 조인 키. |
| `DALYTRAN-ORIG-TS` | `PIC X(26)` | 26 | 원거래 발생 타임스탬프. 형식은 `YYYY-MM-DD HH:MM:SS.mmmmmm`(추측, ISO 8601 변형 26자). |
| `DALYTRAN-PROC-TS` | `PIC X(26)` | 26 | 거래 처리(승인·게이트웨이) 타임스탬프. 동일 26자 형식. |
| `FILLER` | `PIC X(20)` | 20 | 예약 패딩. 현재 미사용. 향후 필드 확장 여지. |

> **바이트 합산 검증**: 16+2+4+10+100+11+9+50+50+10+16+26+26+20 = **350** ✓
>
> **`DALYTRAN-AMT` 주의**: `PIC S9(09)V99`는 USAGE DISPLAY(기본값)이므로 저장 형식은 EBCDIC 문자 11자리이고 마지막 자리에 부호 오버펀치(overpunch)가 적용된다. COMP-3(packed decimal)이 아님에 유의. Java로 읽을 때는 부호 오버펀치 디코딩이 필요하다(`CobolToJava`류 변환 라이브러리 또는 수동 처리).

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.DALYTRAN.PS` — QSAM 순차 파일, RECFM=FB, LRECL=350. POSTTRAN.jcl의 `//DALYTRAN DD` 및 TRANFILE.jcl에서 정의.
  - 이 레코드를 읽는 프로그램: `CBTRN01C.cbl`, `CBTRN02C.cbl`
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. Java DTO 매핑

```java
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DailyTransactionRecord {          // DALYTRAN-RECORD (350 bytes)

    private String  transactionId;             // DALYTRAN-ID       PIC X(16)
    private String  typeCode;                  // DALYTRAN-TYPE-CD  PIC X(02)
    private int     categoryCode;              // DALYTRAN-CAT-CD   PIC 9(04)
    private String  source;                    // DALYTRAN-SOURCE   PIC X(10)
    private String  description;              // DALYTRAN-DESC     PIC X(100)
    private BigDecimal amount;                 // DALYTRAN-AMT      PIC S9(09)V99
    private int     merchantId;               // DALYTRAN-MERCHANT-ID PIC 9(09)
    private String  merchantName;             // DALYTRAN-MERCHANT-NAME PIC X(50)
    private String  merchantCity;             // DALYTRAN-MERCHANT-CITY PIC X(50)
    private String  merchantZip;              // DALYTRAN-MERCHANT-ZIP  PIC X(10)
    private String  cardNumber;               // DALYTRAN-CARD-NUM  PIC X(16)
    private LocalDateTime originTimestamp;    // DALYTRAN-ORIG-TS   PIC X(26)
    private LocalDateTime processTimestamp;   // DALYTRAN-PROC-TS   PIC X(26)
    // FILLER X(20) → 무시 또는 byte[] reserved
}
```

### 2. `DALYTRAN-AMT` — PIC S9(09)V99 DISPLAY 변환

`PIC S9(09)V99`는 **COMP-3(packed decimal)이 아닌 DISPLAY**이므로 EBCDIC 문자열 11바이트다. 마지막 자리에 부호 오버펀치가 인코딩된다.

```
양수 마지막 자리: C (0-9 → {, A-I)
음수 마지막 자리: D (0-9 → }, J-R)
```

Java 변환 예시:

```java
// EBCDIC 바이트 배열 11개를 받아 BigDecimal로 변환
BigDecimal parseSignedDisplay(byte[] raw11) {
    // 1) EBCDIC→ASCII 변환 후 오버펀치 마지막 자리 처리
    // 2) 앞 9자리 + 마지막 2자리를 정수로 조합
    // 3) scale=2 BigDecimal 반환
    // → AWS Mainframe Modernization SDK 또는 Apache Commons COBOL 변환 활용 권장
}
```

`double`/`float` 대신 반드시 `BigDecimal`을 사용해야 금액 정밀도가 보장된다.

### 3. 고정-길이 문자열 처리

모든 `PIC X(n)` 필드는 EBCDIC 공백(`0x40`)으로 우측 패딩된다. Java로 읽을 때:

```java
String raw = new String(bytes, "IBM037").stripTrailing(); // EBCDIC 코드페이지
```

### 4. CVTRA05Y(`TRAN-RECORD`)와의 관계

두 copybook은 바이트 레이아웃이 동일하므로 **단일 Java 클래스**로 통합 표현이 가능하다.

```java
// 통합 접근 예시: 공통 인터페이스 + 두 개의 팩토리/어댑터
interface TransactionRecord { ... }
class DailyTransactionRecord implements TransactionRecord { ... }  // DALYTRAN 파일 입력용
class TransactionRecord implements TransactionRecord { ... }       // TRANSACT VSAM 저장용
```

배치 파이프라인에서 CBTRN02C는 `DALYTRAN-RECORD`를 읽어 `TRAN-RECORD`로 변환·저장하는데, Java로 마이그레이션 시 이 단계를 `BeanUtils.copyProperties()` 또는 MapStruct 매퍼로 처리할 수 있다.

### 5. 타임스탬프 파싱

`PIC X(26)` 타임스탬프는 `YYYY-MM-DD HH:MM:SS.mmmmmm` 형식으로 추정된다(추측 — 원본 프로그램에서 포맷 상수 확인 필요). Java 파싱:

```java
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
LocalDateTime ts = LocalDateTime.parse(raw26.trim(), fmt);
```

### 6. FILLER 20바이트

현재 미사용 패딩이지만 고정-길이 레코드 파서에서 **건너뛰지 말고 읽어야** 총 350바이트가 맞는다. `byte[] reserved = new byte[20]` 또는 `inputStream.skip(20)`으로 처리.
