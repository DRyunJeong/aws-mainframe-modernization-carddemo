# CVACT01Y — 계정 마스터 레코드 레이아웃

- **유형**: Copybook
- **한 줄 요약**: 카드사 계정(Account)의 모든 마스터 정보를 담는 300바이트 고정 길이 레코드를 정의하며, VSAM KSDS 파일 ACCTDATA의 레코드 구조를 온라인·배치 프로그램이 공유한다.

---

## 기능 설명

`CVACT01Y`는 CardDemo 시스템의 **계정 엔티티(Account Entity)**를 표현하는 copybook이다. 소스 주석에 `RECLN 300`으로 명시된 대로 레코드 길이는 정확히 300바이트이며, 마지막 FILLER 178바이트가 미래 확장이나 패딩 용도로 예약되어 있다(소스 17번 라인).

이 copybook은 JCL·VSAM 관점에서 ACCTDATA 데이터셋의 논리적 레코드 구조를 기술한다. COBOL 프로그램에서 `COPY CVACT01Y`를 사용하면 `ACCOUNT-RECORD`라는 01-레벨 그룹 항목이 해당 WORKING-STORAGE 또는 FILE SECTION에 삽입된다. Java 관점에서는 데이터베이스 엔티티 클래스(`@Entity AccountRecord`)에 해당하며, 각 05-레벨 필드가 클래스 멤버 변수에 대응한다.

주요 특징:
- **Primary Key**: `ACCT-ID` PIC 9(11) — VSAM KSDS에서 이 필드가 키 컬럼으로 사용된다.
- **금액 필드 3종**: 현재 잔액, 신용 한도, 현금서비스 한도 모두 `S9(10)V99` DISPLAY 형식. 부호 있는 10자리 정수부 + 2자리 소수부(묵시적 소수점).
- **날짜 필드 3종**: 개설일, 만료일, 재발행일 모두 `X(10)` 문자열 — 저장 포맷은 코드 관례상 `YYYY-MM-DD`로 추정(별도 형식 선언 없음, 추측).
- **순환 주기 합산**: 현재 청구 주기의 입금(`ACCT-CURR-CYC-CREDIT`)·출금(`ACCT-CURR-CYC-DEBIT`) 누적 금액 보관.
- **FILLER 178바이트**: 실제 의미 있는 필드는 122바이트, 나머지는 예약 패딩.

---

## 필드 레이아웃

| 순번 | 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 매핑 |
|------|--------|-------------|--------|-------------------|
| 1 | `ACCT-ID` | `PIC 9(11)` DISPLAY | 11 | 계정 고유 식별자. VSAM KSDS 키 필드. Java: `String accountId` (11자리 0-패딩 문자열) 또는 `long` |
| 2 | `ACCT-ACTIVE-STATUS` | `PIC X(01)` DISPLAY | 1 | 계정 활성 상태 코드. 단일 문자. Java: `char` 또는 enum `AccountStatus` — 실제 코드값('Y'/'N' 등)은 사용 프로그램에서 정의(추측) |
| 3 | `ACCT-CURR-BAL` | `PIC S9(10)V99` DISPLAY | 13\* | 현재 잔액. 부호(1) + 정수 10자리 + 소수 2자리. Java: `BigDecimal` — `new BigDecimal(rawStr).scaleByPowerOfTen(-2)` |
| 4 | `ACCT-CREDIT-LIMIT` | `PIC S9(10)V99` DISPLAY | 13\* | 총 신용 한도. Java: `BigDecimal` |
| 5 | `ACCT-CASH-CREDIT-LIMIT` | `PIC S9(10)V99` DISPLAY | 13\* | 현금서비스(카드론) 한도. Java: `BigDecimal` |
| 6 | `ACCT-OPEN-DATE` | `PIC X(10)` DISPLAY | 10 | 계정 개설일. Java: `LocalDate` — 파싱 포맷 `YYYY-MM-DD` 추정 |
| 7 | `ACCT-EXPIRAION-DATE` | `PIC X(10)` DISPLAY | 10 | 카드 만료일. Java: `LocalDate`. 소스 오타: `EXPIRAION`(T 누락) — 마이그레이션 시 필드명 정정 필요 |
| 8 | `ACCT-REISSUE-DATE` | `PIC X(10)` DISPLAY | 10 | 카드 재발행일. Java: `LocalDate` |
| 9 | `ACCT-CURR-CYC-CREDIT` | `PIC S9(10)V99` DISPLAY | 13\* | 현재 청구 주기 입금(크레딧) 합계. Java: `BigDecimal` |
| 10 | `ACCT-CURR-CYC-DEBIT` | `PIC S9(10)V99` DISPLAY | 13\* | 현재 청구 주기 출금(데빗) 합계. Java: `BigDecimal` |
| 11 | `ACCT-ADDR-ZIP` | `PIC X(10)` DISPLAY | 10 | 청구지 우편번호. Java: `String` (최대 10자, 미국 ZIP+4 또는 한국 5자리 가능) |
| 12 | `ACCT-GROUP-ID` | `PIC X(10)` DISPLAY | 10 | 계정 그룹 식별자(고객 세그먼트, 포인트 그룹 등 용도 추정). Java: `String` |
| 13 | `FILLER` | `PIC X(178)` DISPLAY | 178 | 예약 패딩. Java에서는 무시 또는 `byte[] reserved` |

\* `PIC S9(10)V99` DISPLAY 저장 크기: COBOL에서 `S9(n)` DISPLAY는 숫자 n자리 + 부호 1바이트(또는 trailing/leading sign 방식). IBM Enterprise COBOL 기본값은 trailing numeric sign으로 마지막 바이트 상위 니블에 부호를 인코딩 — 실제로는 12바이트가 될 수 있으나 V(묵시적 소수점)는 물리 바이트를 소비하지 않으므로 `S9(10)V99`는 DISPLAY 시 12바이트(10+2 자리, 부호 포함). 컴파일러·dialect에 따라 다를 수 있으므로 실측 필요.

**총 유효 필드 합계**: 11 + 1 + (12×5) + (10×3) + 10 + 10 + 178 = 11+1+60+30+10+10+178 = **300바이트** (주석의 `RECLN 300`과 일치, 소스 2번 라인).

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — `CVACT01Y` 자체는 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — copybook은 데이터 구조 선언만 담으며 실행 코드가 없다.
- **데이터셋/파일/DB 테이블**: VSAM KSDS `ACCTDATA` (레코드 길이 300, 키 = `ACCT-ID` 11바이트). 이 copybook을 COPY하는 프로그램들이 DD명 `ACCTFILE` 또는 `ACCTDAT` 등으로 이 파일을 열어 사용한다(실제 DD명은 사용 프로그램의 FD/SELECT 또는 JCL에서 확인 필요).
- **트랜잭션 ID 또는 EXEC PGM**: 없음 — copybook은 독립 실행 단위가 아니다.

---

## Java/현대화 노트

### 1. 클래스 매핑

```java
// ACCOUNT-RECORD (CVACT01Y) → Java 엔티티 클래스 예시
public class AccountRecord {
    private String  accountId;           // ACCT-ID         PIC 9(11)
    private char    activeStatus;        // ACCT-ACTIVE-STATUS PIC X(01)
    private BigDecimal currentBalance;   // ACCT-CURR-BAL    S9(10)V99
    private BigDecimal creditLimit;      // ACCT-CREDIT-LIMIT S9(10)V99
    private BigDecimal cashCreditLimit;  // ACCT-CASH-CREDIT-LIMIT S9(10)V99
    private LocalDate  openDate;         // ACCT-OPEN-DATE   X(10)
    private LocalDate  expirationDate;   // ACCT-EXPIRAION-DATE X(10)  ← 오타 정정
    private LocalDate  reissueDate;      // ACCT-REISSUE-DATE X(10)
    private BigDecimal currCycCredit;    // ACCT-CURR-CYC-CREDIT S9(10)V99
    private BigDecimal currCycDebit;     // ACCT-CURR-CYC-DEBIT S9(10)V99
    private String  addrZip;             // ACCT-ADDR-ZIP    X(10)
    private String  groupId;             // ACCT-GROUP-ID    X(10)
    // FILLER 178바이트 — 생략
}
```

### 2. PIC S9(10)V99 → BigDecimal 변환 주의

`V` (Virtual decimal point)는 COBOL에서 묵시적 소수점으로 물리 바이트를 차지하지 않는다. 예를 들어 `ACCT-CURR-BAL`에 원시값 `000000100050`이 저장되어 있으면 실제 값은 `1000.50`이다. Java로 읽을 때:

```java
// 원시 12자리 문자열 → BigDecimal
String raw = "000000100050"; // ACCT-CURR-BAL 원시값 (부호 별도 처리 후)
BigDecimal balance = new BigDecimal(raw).scaleByPowerOfTen(-2); // 1000.50
```

`float`/`double` 사용은 절대 금지 — 금액 계산에서 부동소수점 오차가 발생한다.

### 3. DISPLAY vs COMP-3 — 이 copybook은 DISPLAY

이 레코드의 모든 숫자 필드는 `USAGE COMP-3`(packed decimal)이 아닌 기본 `DISPLAY` 방식이다. 즉 VSAM 파일에 EBCDIC 숫자 문자 그대로 저장된다. 메인프레임에서 Java로 마이그레이션 시 **EBCDIC→ASCII 변환** 후 파싱이 필요하다(DISPLAY 숫자는 EBCDIC 코드포인트 F0–F9 범위 사용).

### 4. 소스 오타: `ACCT-EXPIRAION-DATE`

소스 11번 라인에 `EXPIRAION`으로 T가 누락되어 있다. 현재 시스템의 모든 프로그램이 이 오타를 그대로 사용하므로 마이그레이션 시 Java 필드명은 `expirationDate`(정상 철자)로 정정하되, COBOL 레코드 파서/매퍼 레이어에서 오프셋 기반 매핑이 필요하다.

### 5. FILLER 178바이트 처리

COBOL `FILLER`는 레코드 정렬·확장용 패딩이다. Java 역직렬화 시 이 영역은 읽기만 하고 무시하거나, 향후 필드 추가를 대비해 `byte[] reserved = new byte[178]`로 보관할 수 있다.

### 6. 청구 주기 크레딧/데빗 필드의 의미

`ACCT-CURR-CYC-CREDIT`과 `ACCT-CURR-CYC-DEBIT`은 배치 프로그램(CBACT04C 등 이자 계산 배치)이 각 청구 주기 마감 시 누적 합계를 갱신하는 필드다(에이전트 메모리 batch_and_optional_modules.md 참조). 마이그레이션 시 이 두 필드를 **별도 이력 테이블**로 분리하고 계정 엔티티에서는 조회 전용으로 처리하는 설계를 권장한다.

### 7. VSAM KSDS → 관계형 DB 매핑

| VSAM 개념 | Java/RDB 개념 |
|-----------|---------------|
| ACCTDATA KSDS | `account` 테이블 |
| `ACCT-ID` (VSAM 키) | `PRIMARY KEY account_id CHAR(11)` |
| 고정 300바이트 레코드 | 정규화된 테이블 행 |
| FILLER 178바이트 | 컬럼 없음 (삭제) |

---

*소스 버전: CardDemo_v1.0-15-g27d6c6f-68 (2022-07-19, 소스 19번 라인)*
