# CSSTRPFY — AID→PFKey 공통 저장 로직 (인라인 코드 단편)

- **유형**: Copybook (실행 가능 PROCEDURE 코드 포함 — 데이터 정의 없음)
- **한 줄 요약**: CICS 터미널에서 눌린 키(AID 바이트, `EIBAID`)를 읽어 공통 commarea(`CARDDEMO-COMMAREA`)의 PF키 플래그 필드(`CCARD-AID-*`)로 변환해 저장하는 공유 paragraph 단편.

---

## 기능 설명

이 copybook은 데이터 레이아웃을 정의하지 않는다. 대신 **실행 가능한 COBOL paragraph**(`YYYY-STORE-PFKEY` / `YYYY-STORE-PFKEY-EXIT`) 전체를 텍스트 치환 방식으로 포함(COPY)시키는 **코드 템플릿 copybook**이다.

CICS 온라인 트랜잭션에서 사용자가 키보드 키를 누르면, CICS 커널은 누른 키에 대응하는 1바이트 AID(Attention Identifier) 코드를 `EIB(Executive Interface Block)`의 `EIBAID` 필드에 저장한다. 각 온라인 프로그램은 화면 입력을 수신한 직후 이 paragraph를 `PERFORM`하여 `EIBAID` 원시 바이트를 사람이 읽을 수 있는 88-level 조건 이름(`CCARD-AID-ENTER`, `CCARD-AID-PFK01` 등)으로 변환한다. 이후 프로그램 로직은 `EIBAID`를 직접 비교하는 대신 이 88-level 플래그를 사용함으로써 키 분기 로직을 한 곳에 집중시킨다.

**핵심 동작 요약:**

| 단계 | 설명 |
|------|------|
| 1 | `EVALUATE TRUE` 블록이 `EIBAID`를 CICS 제공 상수(`DFHENTER`, `DFHCLEAR`, `DFHPA1/2`, `DFHPF1`~`DFHPF24`)와 순차 비교한다. |
| 2 | 일치한 상수에 해당하는 `CCARD-AID-*` 88-level 조건 이름을 `SET ... TO TRUE`로 설정한다(해당 필드에 미리 정의된 VALUE 리터럴을 기록). |
| 3 | PF13~PF24는 PF01~PF12의 "shifted" 별칭으로 취급하여 **같은 플래그를 재사용**한다(예: `DFHPF13` → `CCARD-AID-PFK01`, `DFHPF14` → `CCARD-AID-PFK02`). |
| 4 | `YYYY-STORE-PFKEY-EXIT` paragraph의 `EXIT`로 호출자에게 제어를 반환한다. |

> **PF13–24 매핑 주의**: 라인 54–77 참조. CICS 터미널에서는 `SHIFT+PF1 = PF13`, `SHIFT+PF2 = PF14`, … 관계이므로, 이 프로그램은 "shifted" 상위 키를 하위 키와 동일하게 처리한다. Java 마이그레이션 시 이 의도적 alias 처리를 그대로 반영해야 한다.

---

## 필드 레이아웃

이 copybook은 **데이터 항목을 선언하지 않는다.** 필드는 외부 copybook(`COCOM01Y`)에 정의된 `CARDDEMO-COMMAREA` 구조체 안의 `CCARD-AID-*` 그룹에 속한다. 아래는 이 copybook이 참조하는 외부 필드 목록이며, 실제 PIC/USAGE 정의는 `COCOM01Y`에 있다.

| 참조 필드명 | 위치 (copybook) | 의미 |
|-------------|-----------------|------|
| `EIBAID` | CICS EIB (시스템 제공) | 마지막으로 눌린 키의 AID 바이트 (`PIC X(1)`) |
| `DFHENTER` | `DFHAID` (CICS 제공 copybook) | ENTER 키 AID 상수 |
| `DFHCLEAR` | `DFHAID` | CLEAR 키 AID 상수 |
| `DFHPA1`, `DFHPA2` | `DFHAID` | PA(Program Attention) 키 1/2 AID 상수 |
| `DFHPF1`~`DFHPF24` | `DFHAID` | PF(Program Function) 키 1–24 AID 상수 |
| `CCARD-AID-ENTER` | `COCOM01Y` → `CARDDEMO-COMMAREA` | 88-level: ENTER 키 플래그 |
| `CCARD-AID-CLEAR` | `COCOM01Y` → `CARDDEMO-COMMAREA` | 88-level: CLEAR 키 플래그 |
| `CCARD-AID-PA1` | `COCOM01Y` → `CARDDEMO-COMMAREA` | 88-level: PA1 키 플래그 |
| `CCARD-AID-PA2` | `COCOM01Y` → `CARDDEMO-COMMAREA` | 88-level: PA2 키 플래그 |
| `CCARD-AID-PFK01`~`CCARD-AID-PFK12` | `COCOM01Y` → `CARDDEMO-COMMAREA` | 88-level: PF1–PF12(+PF13–24 alias) 플래그 |

> `COCOM01Y`의 `CCARD-AID-*` 88-level 필드 원형은 이 copybook 밖에 정의되어 있다. 이 단편은 "쓰기만" 하며 선언은 하지 않는다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 (이 copybook 자체가 다른 COPY를 포함하지 않음)
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: 없음 (순수 메모리 연산)
- **트랜잭션 ID 또는 EXEC PGM**: 없음

**런타임 의존 (COPY로 주입받는 외부 심볼):**

이 단편이 동작하려면 포함하는 프로그램(CICS 온라인 CO* 프로그램)의 WORKING-STORAGE/LINKAGE에 다음이 먼저 정의되어 있어야 한다:

| 심볼 | 제공 copybook | 비고 |
|------|---------------|------|
| `EIBAID`, EIB 전체 | CICS 런타임 (자동) | `EXEC CICS RECEIVE` 이후 유효 |
| `DFHENTER`, `DFHCLEAR`, `DFHPA1/2`, `DFHPF1`–`DFHPF24` | `DFHAID` | 각 프로그램에서 `COPY DFHAID` 필요 |
| `CCARD-AID-ENTER` 등 `CCARD-AID-*` 88-level 항목 | `COCOM01Y` | `COPY COCOM01Y` → `CARDDEMO-COMMAREA` |

---

## Java/현대화 노트

### 1. 이 copybook의 Java 등가물: `enum` + `switch`

```java
// EIBAID 원시 바이트 → 논리 키 enum 변환 유틸리티
public enum AidKey {
    ENTER, CLEAR, PA1, PA2,
    PF01, PF02, PF03, PF04, PF05, PF06,
    PF07, PF08, PF09, PF10, PF11, PF12,
    UNKNOWN
}

public static AidKey fromAidByte(byte aid) {
    return switch (aid) {
        case DFHENTER -> AidKey.ENTER;
        case DFHCLEAR -> AidKey.CLEAR;
        case DFHPA1   -> AidKey.PA1;
        case DFHPA2   -> AidKey.PA2;
        case DFHPF1, DFHPF13 -> AidKey.PF01; // PF13 = SHIFT+PF1 alias
        case DFHPF2, DFHPF14 -> AidKey.PF02;
        // ... PF03–PF12 / PF15–PF24 동일 패턴
        default -> AidKey.UNKNOWN;
    };
}
```

`CCARD-AID-*` 88-level 플래그 집합 전체가 `AidKey` enum 하나로 대체된다. 세션 상태(commarea)에는 이 enum 값만 저장하면 된다.

### 2. 88-level `SET ... TO TRUE` 패턴

COBOL의 88-level 조건 이름은 Java의 `boolean` 필드나 enum 상수에 대응한다. `SET CCARD-AID-PFK03 TO TRUE`는 내부적으로 부모 `PIC X` 필드에 미리 지정된 VALUE 바이트를 쓰는 연산이다(Boolean을 저장하는 것이 아니라 **특정 문자열 값**을 쓰는 것임에 주의). Java enum은 이 간접성 없이 타입 안전하게 키를 표현할 수 있다.

### 3. PF13–PF24 alias 처리

라인 54–77에서 `DFHPF13` → `CCARD-AID-PFK01`, `DFHPF14` → `CCARD-AID-PFK02` … 순으로 상위 PF 키를 하위 PF 키로 접는다. 이는 CardDemo 화면이 PF13–24를 독립적으로 처리하지 않는다는 설계 결정이다. Java 전환 시 동일한 alias 처리를 `switch` case에 명시적으로 유지해야 한다(묵시적으로 무시해서는 안 됨).

### 4. 코드 템플릿 copybook 패턴 — Java의 `default method` / `mixin` 유사

이 copybook은 데이터가 아닌 **로직**을 공유한다. COBOL COPY는 컴파일 전 텍스트 치환이므로 각 포함 프로그램에 paragraph가 **물리적으로 복제**된다. Java에서는 `interface default method` 또는 `abstract` 기반 클래스의 메서드로 동일 로직을 상속/공유한다. 단, 메서드는 commarea 객체 참조를 인자로 전달받아야 한다(COBOL은 암묵적 공유 메모리 사용).

### 5. CICS 의존성 제거

`EIBAID`와 `DFHAID` 상수는 CICS 런타임 전용이다. 현대화 시:
- `EIBAID`를 HTTP 요청 파라미터/이벤트 키 코드로 교체.
- `DFHENTER`/`DFHPF*` 상수를 애플리케이션 자체 `enum` 또는 상수 클래스로 추상화.
- CICS commarea(`CARDDEMO-COMMAREA`)를 HTTP 세션 객체 또는 `@SessionScoped` CDI 빈으로 대체.

### 6. 불확실 사항

- `CCARD-AID-*` 88-level 필드의 실제 VALUE 바이트는 `COCOM01Y` 소스에 정의되어 있으므로, 정확한 PIC 타입과 매핑 바이트는 `COCOM01Y.cpy` 직접 확인 필요.
- 이 copybook을 실제로 COPY하는 CO* 프로그램 목록은 소스 검색(`grep -r CSSTRPFY app/`) 으로 확인 가능하다(추측: 대부분의 CO* 온라인 프로그램이 포함).
