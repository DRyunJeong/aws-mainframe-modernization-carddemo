# CVTRA01Y — 거래 카테고리 잔액 레코드

- **유형**: Copybook
- **한 줄 요약**: 계정별·거래유형별·거래카테고리별 잔액 합산을 저장하는 VSAM KSDS 파일(TCATBALF)의 50바이트 레코드 레이아웃을 정의한다.

---

## 기능 설명

`CVTRA01Y`는 VSAM KSDS 파일 **TCATBALF**의 레코드 구조를 선언하는 단일-레코드 copybook이다(주석 라인 1-3: `RECLN = 50`).

레코드 한 건은 "특정 계정(ACCT-ID) + 거래유형(TYPE-CD) + 거래카테고리(CAT-CD)" 3원 복합 키에 대한 **누적 잔액(balance)** 하나를 보유한다.

- **배치 포스팅(CBTRN02C)**: 일일 거래 파일(DALYTRAN)을 읽어 각 거래 금액을 해당 복합 키 레코드에 가산(ADD ... TO TRAN-CAT-BAL → REWRITE)하거나, 키가 없으면 신규 레코드를 삽입(WRITE)한다(CBTRN02C 라인 504-539).
- **이자 계산(CBACT04C)**: TCATBALF를 순차 읽기(READ NEXT)하여 `TRAN-CAT-BAL`에 이자율을 곱해 이자를 산출한다(CBACT04C 라인 465: `TRAN-CAT-BAL * DIS-INT-RATE / 1200`).

이 구조는 Java의 `Map<CompositeKey, BigDecimal>` 집계 테이블과 개념적으로 동일하며, VSAM KSDS는 키 순서 접근과 랜덤 접근을 모두 지원한다.

---

## 필드 레이아웃

소스 라인 4-10 기준:

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `TRAN-CAT-KEY` | 그룹(GROUP) | 17 | 복합 기본 키 전체 |
| `TRANCAT-ACCT-ID` | `PIC 9(11)` DISPLAY | 11 | 계정 ID (VSAM KSDS 키의 선두 부분) |
| `TRANCAT-TYPE-CD` | `PIC X(02)` DISPLAY | 2 | 거래 유형 코드 (예: "PU"=구매, "PA"=결제 등) |
| `TRANCAT-CD` | `PIC 9(04)` DISPLAY | 4 | 거래 카테고리 코드 (4자리 숫자 문자열) |
| `TRAN-CAT-BAL` | `PIC S9(09)V99` DISPLAY | 11 | 카테고리 누적 잔액; 부호 포함 9정수+2소수 |
| `FILLER` | `PIC X(22)` DISPLAY | 22 | 예약 패딩 — 현재 미사용 |

**합계**: 17 + 11 + 22 = **50바이트** (주석의 `RECLN = 50`과 일치).

### 주요 필드 상세

**`TRAN-CAT-KEY` (라인 5-8)**
- COBOL GROUP 레벨(하위 필드의 물리적 연결). VSAM KSDS의 KEY는 이 그룹 전체(17바이트)로 정의된다(CBACT04C/CBTRN02C의 `SELECT` 절 KSDS 선언에서 확인).
- Java 대응: 불변 복합 키 객체

```java
record TranCatKey(
    String acctId,     // 11자리 제로-패딩 문자열
    String typeCode,   // 2자 알파코드
    String catCode     // 4자리 숫자 문자열
) {}
```

**`TRAN-CAT-BAL` (라인 9)**
- `PIC S9(09)V99` DISPLAY: 부호(S), 정수부 9자리, 묵시적 소수점(V), 소수부 2자리. DISPLAY 형식이므로 EBCDIC 숫자 문자로 저장; 부호는 마지막 바이트의 상위 니블(overpunch sign) 또는 별도 플러스/마이너스 표시.
- 주의: COMP-3(packed decimal)이 **아니다**. `PIC S9(09)V99 DISPLAY`는 11바이트 EBCDIC 문자열로 저장된다.
- Java 대응: `BigDecimal` (2자리 소수 스케일 고정)

```java
BigDecimal tranCatBal; // scale=2, precision<=11
```

> (추측) CBACT04C 이자 계산(라인 465)에서 이 필드를 정수 COMP 변수로 MOVE 없이 직접 산술에 사용한다. IBM Enterprise COBOL은 DISPLAY 숫자도 산술 연산에 허용하지만, 마이그레이션 시에는 반드시 COMP-3 또는 Binary로의 변환 여부를 확인해야 한다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — `CVTRA01Y` 자체는 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**:
  - VSAM KSDS `TCATBALF` — 이 copybook이 레코드 레이아웃을 정의하는 파일. 키 = `TRAN-CAT-KEY` 17바이트. 관련 JCL: `TCATBALF.jcl`(파일 정의), `POSTTRAN.jcl`(포스팅 배치 실행), `INTCALC.jcl`(이자계산 배치 실행), `PRTCATBL.jcl`(카테고리 잔액 출력), `DEFGDGB.jcl`(GDG 정의).
  - 이 copybook을 COPY하는 프로그램: `CBTRN02C.cbl`(포스팅, READ/WRITE/REWRITE), `CBACT04C.cbl`(이자계산, READ NEXT).
- **트랜잭션 ID 또는 EXEC PGM**: 없음 — 배치 전용 레코드; CICS 온라인 프로그램에서 직접 참조하는 사례는 확인되지 않음.

---

## Java/현대화 노트

### 1. 레코드 → DTO/Entity 매핑

```java
import java.math.BigDecimal;

/**
 * TCATBALF VSAM KSDS 레코드에 대응하는 DTO.
 * COBOL: TRAN-CAT-BAL-RECORD (CVTRA01Y, RECLN=50)
 */
public class TranCatBalRecord {

    // TRAN-CAT-KEY (17바이트 복합 KSDS 키)
    private String trancatAcctId;   // PIC 9(11) — 계정 ID
    private String trancatTypeCd;   // PIC X(02) — 거래 유형 코드
    private String trancatCd;       // PIC 9(04) — 거래 카테고리 코드

    // TRAN-CAT-BAL: PIC S9(09)V99 → BigDecimal(scale=2)
    private BigDecimal tranCatBal;

    // FILLER PIC X(22) — 직렬화/역직렬화 시에만 필요, DTO에서는 생략 가능
}
```

### 2. DISPLAY 숫자의 BigDecimal 변환

COBOL `PIC S9(09)V99 DISPLAY` 필드를 파일에서 읽을 때, IBM EBCDIC 인코딩의 overpunch sign을 처리해야 한다. 단순 ASCII 파싱으로는 부호가 깨진다.

```java
// EBCDIC → BigDecimal 변환 예시 (라이브러리 또는 직접 구현 필요)
// "00000123400}" 같은 overpunch 마지막 문자를 처리해야 함.
// org.apache.camel, IBM JTOpen, 또는 net.sf.cb2java 같은 라이브러리 활용 권장.
BigDecimal amount = EbcdicConverter.parseSignedDecimal(rawBytes, 9, 2);
```

### 3. VSAM KSDS → 관계형 DB/NoSQL

| VSAM 개념 | Java/현대 대응 |
|---|---|
| KSDS (키-순서 데이터셋) | PK 인덱스 있는 관계형 테이블 또는 sorted Map |
| 복합 키 `TRAN-CAT-KEY` | 복합 PK `(acct_id, type_cd, cat_cd)` |
| READ ... KEY IS | SELECT WHERE pk = ? |
| READ NEXT (순차) | 커서/스크롤, JPA Pageable |
| WRITE / REWRITE | INSERT ON CONFLICT UPDATE (upsert) |
| FILE STATUS '23' | 키 미존재 (NOT FOUND) 예외 처리 |

### 4. 이자 계산 정밀도 주의 (CBACT04C 라인 465)

```cobol
COMPUTE ... = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
```

COBOL의 `COMPUTE`는 중간 결과를 선언된 PIC 크기로 잘라낸다. Java 마이그레이션 시 `BigDecimal.multiply().divide()` + 명시적 `RoundingMode` 지정이 필수다. `double`/`float` 사용은 금융 데이터이므로 절대 금지.

```java
BigDecimal interest = tranCatBal
    .multiply(disIntRate)
    .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
```

### 5. 22바이트 FILLER 처리

현재 미사용이지만 고정 길이 레코드 직렬화 시 반드시 포함해 **총 50바이트**를 유지해야 한다. DB나 JSON API로 전환 시에는 제거 가능하다.
