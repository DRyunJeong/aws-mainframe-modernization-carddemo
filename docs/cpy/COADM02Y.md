# COADM02Y — 관리자 메뉴 옵션 테이블

- **유형**: Copybook
- **한 줄 요약**: 관리자 전용 화면(COADM0*C)에서 표시할 메뉴 항목 목록(번호·이름·PGMNAME)을 정적 데이터로 정의하고, REDEFINES+OCCURS로 배열 뷰를 얹는 공유 테이블 copybook.

---

## 기능 설명

`COADM02Y`는 CardDemo 관리자 메뉴 화면이 표시할 수 있는 메뉴 옵션의 **정적 룩업 테이블**이다. COBOL의 `COPY` 문으로 이 copybook을 포함하는 프로그램은 `WORKING-STORAGE SECTION`에 `CARDDEMO-ADMIN-MENU-OPTIONS` 01 레벨 레코드를 얻는다.

이 copybook이 사용하는 핵심 패턴은 **FILLER 채우기 → REDEFINES 배열 오버레이**다.

1. `CDEMO-ADMIN-OPTIONS-DATA`(05 레벨)에 각 메뉴 항목을 순서대로 FILLER 필드로 나열해 선형 바이트 블록으로 초기화한다.
2. `CDEMO-ADMIN-OPTIONS`(05 레벨)가 `REDEFINES CDEMO-ADMIN-OPTIONS-DATA`로 같은 메모리를 재해석해, `CDEMO-ADMIN-OPT OCCURS 9 TIMES`라는 배열로 읽을 수 있게 한다.

결과적으로 프로그램은 `CDEMO-ADMIN-OPT(I)-OPT-NUM`, `CDEMO-ADMIN-OPT(I)-OPT-NAME`, `CDEMO-ADMIN-OPT(I)-OPT-PGMNAME`으로 I번째 메뉴 항목에 접근하며, `CDEMO-ADMIN-OPT-COUNT`(값 6)로 실제 활성 항목 수를 제한한다.

### DB2 옵션 추가 이력

소스 코드의 주석(`00200000`, `00230000`, `00450002`, `00500002`)에서 두 차례 확장을 확인할 수 있다.

- 원래 옵션 수는 4개(`VALUE 4`, 현재 주석 처리됨 — 라인 `002100`).
- DB2 V1 릴리스에서 옵션 5(거래 유형 조회·수정)와 옵션 6(거래 유형 유지보수)이 추가돼 현재 `VALUE 6`으로 활성화됨(라인 `002200`).
- OCCURS 크기는 9로 선언돼 있어 현재 데이터(6개)에 대해 3슬롯이 미사용 상태다.

---

## 필드 레이아웃

### 01 레벨: CARDDEMO-ADMIN-MENU-OPTIONS

| 필드명 | 레벨 | PIC / USAGE | 길이 | 초기값 / 설명 |
|---|---|---|---|---|
| `CDEMO-ADMIN-OPT-COUNT` | 05 | `PIC 9(02)` | 2 B (DISPLAY) | `VALUE 6` — 활성 메뉴 항목 수. 이전 `VALUE 4` 버전은 주석 처리(라인 002100). |
| `CDEMO-ADMIN-OPTIONS-DATA` | 05 | (그룹) | 270 B | FILLER로 구성된 선형 초기화 블록 (6항목 × 45 B = 270 B). 프로그램이 직접 이 필드명으로 접근하지 않음. |
| ↳ FILLER (옵션 1 번호) | 10 | `PIC 9(02)` | 2 B | `VALUE 1` |
| ↳ FILLER (옵션 1 이름) | 10 | `PIC X(35)` | 35 B | `'User List (Security)               '` |
| ↳ FILLER (옵션 1 PGMNAME) | 10 | `PIC X(08)` | 8 B | `'COUSR00C'` |
| ↳ FILLER (옵션 2 번호) | 10 | `PIC 9(02)` | 2 B | `VALUE 2` |
| ↳ FILLER (옵션 2 이름) | 10 | `PIC X(35)` | 35 B | `'User Add (Security)                '` |
| ↳ FILLER (옵션 2 PGMNAME) | 10 | `PIC X(08)` | 8 B | `'COUSR01C'` |
| ↳ FILLER (옵션 3 번호) | 10 | `PIC 9(02)` | 2 B | `VALUE 3` |
| ↳ FILLER (옵션 3 이름) | 10 | `PIC X(35)` | 35 B | `'User Update (Security)             '` |
| ↳ FILLER (옵션 3 PGMNAME) | 10 | `PIC X(08)` | 8 B | `'COUSR02C'` |
| ↳ FILLER (옵션 4 번호) | 10 | `PIC 9(02)` | 2 B | `VALUE 4` |
| ↳ FILLER (옵션 4 이름) | 10 | `PIC X(35)` | 35 B | `'User Delete (Security)             '` |
| ↳ FILLER (옵션 4 PGMNAME) | 10 | `PIC X(08)` | 8 B | `'COUSR03C'` |
| ↳ FILLER (옵션 5 번호) | 10 | `PIC 9(02)` | 2 B | `VALUE 5` — DB2 V1 추가 |
| ↳ FILLER (옵션 5 이름) | 10 | `PIC X(35)` | 35 B | `'Transaction Type List/Update (Db2) '` |
| ↳ FILLER (옵션 5 PGMNAME) | 10 | `PIC X(08)` | 8 B | `'COTRTLIC'` |
| ↳ FILLER (옵션 6 번호) | 10 | `PIC 9(02)` | 2 B | `VALUE 6` — DB2 V1 추가 |
| ↳ FILLER (옵션 6 이름) | 10 | `PIC X(35)` | 35 B | `'Transaction Type Maintenance (Db2) '` |
| ↳ FILLER (옵션 6 PGMNAME) | 10 | `PIC X(08)` | 8 B | `'COTRTUPC'` |
| `CDEMO-ADMIN-OPTIONS` | 05 | **REDEFINES** `CDEMO-ADMIN-OPTIONS-DATA` (그룹) | 270 B | FILLER 블록과 동일 메모리를 배열로 재해석. |
| ↳ `CDEMO-ADMIN-OPT` | 10 | **OCCURS 9 TIMES** (그룹) | 45 B × 9 = 405 B (선언) | 항목당 45 B = 2+35+8. 현재 데이터는 6항목만 초기화(270 B). 나머지 3슬롯(270~405 B 구간)은 미초기화. |
| ↳↳ `CDEMO-ADMIN-OPT-NUM` | 15 | `PIC 9(02)` | 2 B | 메뉴 번호 (1~6) |
| ↳↳ `CDEMO-ADMIN-OPT-NAME` | 15 | `PIC X(35)` | 35 B | 화면에 표시할 메뉴 항목명 (우측 공백 패딩) |
| ↳↳ `CDEMO-ADMIN-OPT-PGMNAME` | 15 | `PIC X(08)` | 8 B | `XCTL` 또는 `LINK` 대상 CICS 프로그램 이름 |

**참고: REDEFINES와 OCCURS 크기 불일치**

`CDEMO-ADMIN-OPTIONS-DATA`의 실제 초기화 데이터는 6항목 × 45 B = **270 B**이지만, `CDEMO-ADMIN-OPTIONS`의 `OCCURS 9 TIMES`는 논리적으로 9 × 45 = **405 B**를 기대한다. COBOL 컴파일러는 REDEFINES 크기 불일치를 오류로 처리하지 않는 경우가 많으며(구현 의존), 실제로 프로그램은 `CDEMO-ADMIN-OPT-COUNT`(=6)로 반복 범위를 제한하므로 미초기화 슬롯에는 접근하지 않는다. Java 마이그레이션 시에는 List의 size()로 경계를 명확히 표현해야 한다.

---

### 관리자 메뉴 옵션 요약표

| 옵션 번호 | 표시 이름 | 이동 프로그램 | 추가 시점 |
|---|---|---|---|
| 1 | User List (Security) | `COUSR00C` | 기본 |
| 2 | User Add (Security) | `COUSR01C` | 기본 |
| 3 | User Update (Security) | `COUSR02C` | 기본 |
| 4 | User Delete (Security) | `COUSR03C` | 기본 |
| 5 | Transaction Type List/Update (Db2) | `COTRTLIC` | DB2 V1 릴리스 |
| 6 | Transaction Type Maintenance (Db2) | `COTRTUPC` | DB2 V1 릴리스 |

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — copybook은 데이터 정의만 포함하며 PROCEDURE DIVISION이 없다. PGMNAME 필드에 기록된 `COUSR00C`, `COUSR01C`, `COUSR02C`, `COUSR03C`, `COTRTLIC`, `COTRTUPC`는 이 copybook을 포함한 관리자 메뉴 프로그램(예: `COADM0*C`)이 런타임에 `EXEC CICS XCTL` 대상으로 사용하는 프로그램 이름이다.
- **데이터셋/파일/DB 테이블**: 없음 — 모든 데이터는 컴파일 시점에 VALUE 절로 확정되는 정적 상수이다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. FILLER → REDEFINES → OCCURS 패턴의 Java 대응

이 copybook의 핵심 패턴은 Java에 직접 대응하는 구문이 없다. COBOL은 메모리를 "한 번 바이트로 쓰고, 다른 타입으로 읽는" 오버레이 기법을 기본 제공한다. Java에서는 동일한 의미를 불변 List로 표현한다.

```java
// COBOL: CDEMO-ADMIN-OPT-COUNT VALUE 6
// COBOL: CDEMO-ADMIN-OPT OCCURS 9 TIMES (6개만 초기화)
public record AdminMenuOption(int optNum, String optName, String pgmName) {}

public final class AdminMenuOptions {

    // CDEMO-ADMIN-OPT-COUNT에 해당
    public static final int OPTION_COUNT = 6;

    // CDEMO-ADMIN-OPTIONS-DATA + REDEFINES CDEMO-ADMIN-OPTIONS에 해당
    public static final List<AdminMenuOption> OPTIONS = List.of(
        new AdminMenuOption(1, "User List (Security)",               "COUSR00C"),
        new AdminMenuOption(2, "User Add (Security)",                "COUSR01C"),
        new AdminMenuOption(3, "User Update (Security)",             "COUSR02C"),
        new AdminMenuOption(4, "User Delete (Security)",             "COUSR03C"),
        new AdminMenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC"),
        new AdminMenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC")
    );
}
```

### 2. `PIC X(35)` 우측 공백 패딩 주의

COBOL의 `PIC X(35)` 필드는 고정 35바이트이며 짧은 문자열은 우측 공백으로 패딩된다(`'User List (Security)               '`). Java 마이그레이션 시 `.trim()` 또는 `strip()`을 반드시 적용해야 UI에 불필요한 공백이 나타나지 않는다.

### 3. `PIC 9(02)` DISPLAY 저장 형식

`CDEMO-ADMIN-OPT-COUNT`와 `CDEMO-ADMIN-OPT-NUM`은 `PIC 9(02) DISPLAY` 형식으로, 메인프레임에서 EBCDIC 숫자 문자 2바이트로 저장된다. Java의 `int` 또는 `byte`로 직접 매핑 가능하나, EBCDIC 바이너리 파일을 직접 파싱하는 경우 EBCDIC-to-ASCII 변환이 필요하다.

### 4. OCCURS 9 vs 실제 데이터 6개 — 경계 오류 위험

COBOL 프로그램은 `CDEMO-ADMIN-OPT-COUNT`(=6)로 반복을 제어하므로 항목 7~9에 접근하지 않는다. Java로 변환 시 이 암묵적 경계를 `List.size()` 또는 명시적 상수로 반드시 문서화해야 한다. 그렇지 않으면 배열 인덱스 7~9에 미초기화 데이터(COBOL에서는 공백/0)가 접근될 수 있다.

### 5. 정적 테이블의 외부화 권장

이 copybook은 메뉴 데이터를 소스 코드에 하드코딩한다. 새 메뉴 항목 추가 시 copybook 수정 → 재컴파일 → 재배포가 필요하다. Java 현대화 시 데이터베이스 테이블(예: `ADMIN_MENU_OPTIONS`), 설정 파일(YAML/JSON), 또는 Spring의 `@ConfigurationProperties`로 외부화해 런타임 변경이 가능한 구조로 전환하는 것을 권장한다.

### 6. PGMNAME 필드의 CICS XCTL 의미

`CDEMO-ADMIN-OPT-PGMNAME`의 8자리 값은 CICS 환경에서 `EXEC CICS XCTL PROGRAM(CDEMO-ADMIN-OPT-PGMNAME(I))`로 사용되는 프로그램 이름이다. Java/Spring MVC 환경에서는 이 필드가 `@RequestMapping` 경로 또는 컨트롤러 클래스 이름에 대응하며, 데이터 주도 내비게이션(data-driven navigation) 패턴으로 구현할 수 있다.

---

*소스: `/app/cpy/COADM02Y.cpy` | CardDemo_v2.0-16-gbdcb6ea-226 | 2024-01-21*
