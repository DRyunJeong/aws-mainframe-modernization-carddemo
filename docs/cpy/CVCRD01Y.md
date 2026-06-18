# CVCRD01Y — 카드 화면 공유 작업 영역 Copybook

- **유형**: Copybook (온라인 CICS 프로그램 간 공유 작업 영역 / 내비게이션 상태)
- **한 줄 요약**: CICS 카드 관련 온라인 프로그램들이 화면 전환, 키 입력 판별, 오류/반환 메시지, 계정·카드·고객 ID 전달에 공통으로 사용하는 작업 변수 집합이다.

---

## 기능 설명

`CVCRD01Y`는 카드(Credit Card) 업무를 처리하는 CICS 온라인 프로그램(예: `COCRDLIC`, `COCRDSLC`, `COCRDUPC` 등 `CO*CRD*` 계열)이 자신의 `WORKING-STORAGE SECTION`에 `COPY CVCRD01Y`로 포함하는 공유 레이아웃이다.

이 copybook의 핵심 역할은 두 가지다.

1. **AID(Attention Identifier) 판별**: 사용자가 터미널에서 누른 키(Enter, Clear, PF01~PF12 등)를 5바이트 문자열로 저장하고, 88-level 조건명으로 가독성 있게 비교할 수 있게 한다.
2. **화면 내비게이션 상태 전달**: 다음에 실행할 프로그램 이름(`CCARD-NEXT-PROG`), mapset/map 이름(`CCARD-NEXT-MAPSET` / `CCARD-NEXT-MAP`), 오류 메시지(`CCARD-ERROR-MSG`), 반환 메시지(`CCARD-RETURN-MSG`)를 보유해 프로그램 간 화면 전환 시 컨텍스트를 유지한다.
3. **식별 키 이중 뷰**: 계정 ID(`CC-ACCT-ID`), 카드 번호(`CC-CARD-NUM`), 고객 ID(`CC-CUST-ID`)를 각각 문자형(PIC X)과 숫자형(PIC 9, REDEFINES)으로 동시에 접근할 수 있도록 정의한다.

이 구조는 Java로 치면 CICS 화면 세션을 가로지르는 `CardWorkContext` DTO 클래스에 해당하며, COMMAREA(`COCOM01Y`)보다 카드 화면에 특화된 작업 영역이다.

---

## 필드 레이아웃

> 소스: `/app/cpy/CVCRD01Y.cpy` (lines 1–47)
>
> 최상위 그룹: `CC-WORK-AREAS` (01) > `CC-WORK-AREA` (05)

### AID 및 내비게이션 필드

| 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 매핑 |
|---|---|---|---|
| `CCARD-AID` | `PIC X(5)` | 5 | 사용자가 마지막으로 누른 AID 키를 나타내는 5자 문자열. Java: `String aidKey` (고정 5자). EIBAID를 이 필드로 변환해 저장함. |
| (88) `CCARD-AID-ENTER` | VALUE `'ENTER'` | — | Enter 키. Java: `aidKey.equals("ENTER")` |
| (88) `CCARD-AID-CLEAR` | VALUE `'CLEAR'` | — | Clear 키(화면 초기화). Java: `aidKey.equals("CLEAR")` |
| (88) `CCARD-AID-PA1` | VALUE `'PA1  '` | — | PA1 키(5자 패딩 포함). Java: `aidKey.equals("PA1  ")` |
| (88) `CCARD-AID-PA2` | VALUE `'PA2  '` | — | PA2 키. |
| (88) `CCARD-AID-PFK01`~`PFK12` | VALUE `'PFK01'`~`'PFK12'` | — | PF1~PF12 기능키. Java: `enum AidKey { ENTER, CLEAR, PA1, PA2, PF1, ..., PF12 }` 로 모델링 권장. |
| `CCARD-NEXT-PROG` | `PIC X(8)` | 8 | 다음에 XCTL(transfer control)할 CICS 프로그램 이름. Java: `String nextProgramId` (CICS 프로그램명은 최대 8자). |
| `CCARD-NEXT-MAPSET` | `PIC X(7)` | 7 | 다음에 SEND MAP할 BMS mapset 이름. Java: `String nextMapset`. |
| `CCARD-NEXT-MAP` | `PIC X(7)` | 7 | 다음에 SEND MAP할 BMS map 이름. Java: `String nextMap`. |
| `CCARD-ERROR-MSG` | `PIC X(75)` | 75 | 화면에 표시할 오류 메시지. Java: `String errorMessage` (최대 75자 고정 버퍼). |
| `CCARD-RETURN-MSG` | `PIC X(75)` | 75 | 이전 프로그램이 남긴 반환/안내 메시지. Java: `String returnMessage`. |
| (88) `CCARD-RETURN-MSG-OFF` | VALUE `LOW-VALUES` | — | 반환 메시지가 비어 있음. Java: `returnMessage == null \|\| returnMessage.isBlank()` |

### 식별 키 필드 (문자형 + 숫자형 REDEFINES)

| 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 매핑 |
|---|---|---|---|
| `CC-ACCT-ID` | `PIC X(11)` VALUE SPACES | 11 | 계정 ID (문자형, 기본값 공백). Java: `String accountId` (11자 고정). |
| `CC-ACCT-ID-N` | `PIC 9(11)` REDEFINES `CC-ACCT-ID` | 11 | 동일 메모리를 숫자로 읽는 뷰. Java 직접 대응 없음 — `Long.parseLong(accountId.trim())` 으로 변환. |
| `CC-CARD-NUM` | `PIC X(16)` VALUE SPACES | 16 | 카드 번호 (문자형, 기본값 공백). Java: `String cardNumber` (16자 고정, PAN). |
| `CC-CARD-NUM-N` | `PIC 9(16)` REDEFINES `CC-CARD-NUM` | 16 | 카드 번호를 숫자 뷰로 접근. Java: `new BigInteger(cardNumber.trim())` 또는 Luhn 검증 시 사용. |
| `CC-CUST-ID` | `PIC X(09)` VALUE SPACES | 9 | 고객 ID (문자형, 기본값 공백). Java: `String customerId` (9자 고정). |
| `CC-CUST-ID-N` | `PIC 9(9)` REDEFINES `CC-CUST-ID` | 9 | 고객 ID를 숫자 뷰로 접근. Java: `Integer.parseInt(customerId.trim())`. |

### 주석 처리된(비활성) 필드

소스 라인 앞에 `*`가 붙어 컴파일에서 제외된 필드들이다. 버전 이력 또는 설계 변경 흔적으로, Java 마이그레이션 시 불필요하므로 제외해도 무방하다.

| 주석 필드명 | PIC | 비고 |
|---|---|---|
| `CCARD-LAST-PROG` | `PIC X(8)` | 이전 프로그램명 (비활성) |
| `CCARD-RETURN-TO-PROG` | `PIC X(8)` | 복귀 대상 프로그램 (비활성) |
| `CCARD-RETURN-FLAG` | `PIC X(1)` | 복귀 플래그 (비활성); 88-level ON/OFF도 주석 처리됨 |
| `CCARD-FUNCTION` | `PIC X(1)` | 기능 코드 (비활성); 88 NO-VALUE/GET-DATA도 주석 처리됨 |

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook은 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — copybook 자체는 실행 단위가 아니다. `COPY CVCRD01Y`로 포함하는 프로그램이 실제 호출 관계를 가진다.
- **데이터셋/파일/DB 테이블**: 없음 — 파일 레코드 레이아웃이 아니라 작업 변수 영역이므로 어떤 VSAM/QSAM 파일과도 직접 연결되지 않는다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음 — 이 copybook을 사용하는 프로그램들이 각자의 CICS 트랜잭션 ID를 보유한다.

---

## Java/현대화 노트

### 1. 전체 구조를 Java DTO로 매핑

```java
public class CardWorkContext {
    // AID 키: enum으로 대체
    private AidKey aidKey;

    // 내비게이션 상태
    private String nextProgramId;   // CC-NEXT-PROG, 8자
    private String nextMapset;      // CCARD-NEXT-MAPSET, 7자
    private String nextMap;         // CCARD-NEXT-MAP, 7자

    // 메시지
    private String errorMessage;    // CCARD-ERROR-MSG, 최대 75자
    private String returnMessage;   // CCARD-RETURN-MSG, 최대 75자

    // 식별 키 (문자형만 보유; 숫자 변환은 getter에서 처리)
    private String accountId;       // CC-ACCT-ID, 11자
    private String cardNumber;      // CC-CARD-NUM, 16자
    private String customerId;      // CC-CUST-ID, 9자

    public Long getAccountIdAsLong() {
        return accountId.isBlank() ? null : Long.parseLong(accountId.trim());
    }
    public BigInteger getCardNumberAsBigInteger() {
        return cardNumber.isBlank() ? null : new BigInteger(cardNumber.trim());
    }
    public Integer getCustomerIdAsInt() {
        return customerId.isBlank() ? null : Integer.parseInt(customerId.trim());
    }
}

public enum AidKey {
    ENTER, CLEAR, PA1, PA2,
    PF1, PF2, PF3, PF4, PF5, PF6,
    PF7, PF8, PF9, PF10, PF11, PF12,
    UNKNOWN
}
```

### 2. REDEFINES 주의사항

`CC-ACCT-ID-N REDEFINES CC-ACCT-ID`는 동일한 11바이트 메모리를 숫자(DISPLAY 숫자, 즉 EBCDIC 문자 `0`~`9`)로 재해석하는 것이다. Java에는 직접 대응 구조가 없다. 반드시 문자열로 저장하고, 숫자가 필요할 때 명시적으로 파싱해야 한다. `CC-ACCT-ID`에 공백이 들어 있으면 `CC-ACCT-ID-N`으로 읽을 때 런타임 오류 발생 가능 — VALUE SPACES 초기값이 이를 방지하도록 설계되어 있다.

### 3. 88-level 조건명 → enum / boolean 메서드

COBOL의 `IF CCARD-AID-PFK03`은 Java에서 `aidKey == AidKey.PF3`에 해당한다. 88-level은 단순 상수 비교이므로 Java enum이 가장 자연스러운 대응이다.

`CCARD-RETURN-MSG-OFF`(VALUE LOW-VALUES)는 빈 문자열/null 체크로 매핑한다. COBOL의 `LOW-VALUES`는 모든 바이트가 `0x00`인 상태로, Java `String`의 공백(`' '`)과 다르다 — EBCDIC 변환 시 ` `으로 처리해야 한다.

### 4. 주석 처리된 필드의 현대화 함의

`CCARD-LAST-PROG`, `CCARD-RETURN-TO-PROG`, `CCARD-RETURN-FLAG`, `CCARD-FUNCTION`이 주석 처리되어 있다. 이는 기능이 설계 단계에서 축소되었거나 COMMAREA(`COCOM01Y`의 `CDEMO-FROM/TO-PROGRAM`)로 통합된 것으로 추정된다(추측). Java 마이그레이션 시 이 필드들을 부활시킬 필요 없이 `COCOM01Y`의 내비게이션 필드를 활용하면 된다.

### 5. 고정 길이 문자열 주의

`CCARD-NEXT-PROG(8)`, `CCARD-NEXT-MAPSET(7)`, `CCARD-NEXT-MAP(7)` 등 모든 문자 필드는 EBCDIC 고정 길이다. Java로 전환할 때 `String.trim()`을 적용하지 않으면 공백 패딩이 비교를 방해한다. Spring MVC/REST 기반으로 전환할 경우 이 필드들은 라우팅 로직(`@RequestMapping`, `ModelAndView`)으로 대체된다.

### 6. 버전 정보

소스 말미의 주석(line 45): `CardDemo_v1.0-15-g27d6c6f-68  Date: 2022-07-19 23:16:00 CDT` — git 해시 `27d6c6f`로 추적 가능.
