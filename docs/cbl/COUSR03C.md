# COUSR03C — 사용자 삭제 (CICS 온라인)

- **유형**: CICS 온라인 COBOL (Pseudo-Conversational)
- **한 줄 요약**: USRSEC VSAM 파일에서 특정 사용자 레코드를 조회·확인 후 삭제하는 관리자 전용 화면 프로그램 (트랜잭션 ID: `CU03`)

---

## 기능 설명

COUSR03C는 CardDemo 관리자 메뉴(COADM01C)에서 호출되며, 운영자가 User ID를 입력하면 USRSEC 파일에서 해당 사용자를 검색해 이름·유형을 화면에 표시하고, 명시적으로 PF5를 누를 때만 삭제를 실행하는 2단계 확인(read-then-delete) 구조를 채택한다.

주요 동작 단계:

1. **조회(Enter 키)**: User ID 입력 → USRSEC 파일 READ(UPDATE 잠금 포함) → 성공 시 이름·유형 표시 + "Press PF5 key to delete…" 안내 메시지
2. **삭제(PF5 키)**: 재검증 후 USRSEC 파일 DELETE → 성공 시 "User xxx has been deleted…" 녹색 메시지 + 화면 초기화
3. **취소/뒤로(PF3·PF12)**: 이전 프로그램(CDEMO-FROM-PROGRAM, 기본값 `COADM01C`)으로 XCTL
4. **화면 초기화(PF4)**: 모든 입력 필드 공백으로 리셋

---

## 입력 / 출력

**입력**:

| 소스 | 내용 |
|---|---|
| BMS 화면 `COUSR3AI` | 운영자가 입력한 User ID (`USRIDINI`, PIC X(8)) |
| `DFHCOMMAREA` (`CARDDEMO-COMMAREA`) | 이전 프로그램에서 넘겨받은 세션 상태: `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-REENTER(88)`, `CDEMO-CU03-USR-SELECTED` 등 |
| USRSEC VSAM 파일 | 사용자 레코드 조회 (키: `SEC-USR-ID`) |

**출력**:

| 대상 | 내용 |
|---|---|
| BMS 화면 `COUSR3AO` | 조회된 이름(`FNAMEO`/`LNAMEO`), 유형(`USRTYPEO`), 메시지(`ERRMSGO`) |
| USRSEC VSAM 파일 | 삭제 성공 시 해당 레코드 제거 |
| `CARDDEMO-COMMAREA` | 다음 트랜잭션으로 전달되는 세션 상태 (RETURN/XCTL 모두) |

---

## 의존성

**COPY (카피북)**:

| 카피북 | 경로 | 역할 |
|---|---|---|
| `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | 전체 애플리케이션 공유 commarea (`CARDDEMO-COMMAREA`) |
| `COUSR03` | `app/cpy-bms/COUSR03.CPY` | BMS 컴파일 생성 심볼릭 맵 (`COUSR3AI`/`COUSR3AO`, REDEFINES 구조) |
| `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | 화면 제목 상수 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
| `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | 현재 날짜·시간 포맷 변수 (`WS-CURDATE-DATA` 등) |
| `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | 공통 메시지 상수 (`CCDA-MSG-INVALID-KEY`) |
| `CSUSR01Y` | `app/cpy/CSUSR01Y.cpy` | USRSEC 파일 레코드 레이아웃 (`SEC-USER-DATA`) |
| `DFHAID` | CICS 시스템 | 키 상수 (`DFHENTER`, `DFHPF3`, `DFHPF4`, `DFHPF5`, `DFHPF12`) |
| `DFHBMSCA` | CICS 시스템 | 화면 속성 상수 (`DFHGREEN`, `DFHNEUTR`) |

> 참고: `app/cpy/COUSR03.cpy`는 존재하지 않으며, BMS 컴파일러가 생성한 `app/cpy-bms/COUSR03.CPY`가 해당 COPY 문의 실체이다. (소스 60번째 줄 `COPY COUSR03.` → 컴파일 시 `cpy-bms/COUSR03.CPY` 사용)

**호출 프로그램 (CALL/XCTL/LINK)**:

| 방향 | 프로그램 | 조건 |
|---|---|---|
| 진입(XCTL from) | `COADM01C` (관리자 메뉴) | 통상 경로. `CDEMO-FROM-PROGRAM`에 기록됨 |
| 진입(XCTL from) | 사용자 목록 화면(추측: `COUSR00C` 계열) | `CDEMO-CU03-USR-SELECTED`에 User ID가 채워진 채로 진입하는 경우 |
| 복귀(XCTL to) | `CDEMO-FROM-PROGRAM` (기본 `COADM01C`) | PF3·PF12 (라인 113~125) |
| 복귀(XCTL to) | `COSGN00C` | EIBCALEN=0 이거나 CDEMO-TO-PROGRAM이 공백인 경우 (라인 91, 200) |

CALL 또는 LINK는 없음. 모든 화면 이동은 XCTL이다.

**데이터셋/파일/DB 테이블**:

| 이름 | 유형 | 접근 방식 |
|---|---|---|
| `USRSEC` | VSAM KSDS | `EXEC CICS READ ... UPDATE` (조회+잠금), `EXEC CICS DELETE` (삭제); 키 = `SEC-USR-ID` PIC X(8) |

DB2/SQL 없음.

**트랜잭션 ID 또는 EXEC PGM**:

- 트랜잭션 ID: **`CU03`** (라인 37, `WS-TRANID`)
- EXEC PGM 방식 아님 (CICS 온라인 트랜잭션)

---

## 핵심 로직 흐름

```
[터미널 → CICS]
      │
      ▼
MAIN-PARA
  ├─ EIBCALEN = 0? ──YES──► CDEMO-TO-PROGRAM = 'COSGN00C'
  │                          RETURN-TO-PREV-SCREEN (XCTL)
  │
  └─ EIBCALEN > 0
       │  MOVE DFHCOMMAREA → CARDDEMO-COMMAREA
       │
       ├─ NOT CDEMO-PGM-REENTER (첫 진입)
       │    SET CDEMO-PGM-REENTER = TRUE
       │    MOVE LOW-VALUES → COUSR3AO  (화면 초기화)
       │    ├─ CDEMO-CU03-USR-SELECTED 채워져 있음?
       │    │    YES ──► USRIDINI ← USR-SELECTED
       │    │            PERFORM PROCESS-ENTER-KEY  (자동 조회)
       │    └─ PERFORM SEND-USRDEL-SCREEN
       │
       └─ CDEMO-PGM-REENTER (재진입: 사용자 입력 처리)
            PERFORM RECEIVE-USRDEL-SCREEN
            EVALUATE EIBAID
              DFHENTER ──► PROCESS-ENTER-KEY
              DFHPF3   ──► RETURN-TO-PREV-SCREEN (XCTL ← FROM-PROGRAM)
              DFHPF4   ──► CLEAR-CURRENT-SCREEN
              DFHPF5   ──► DELETE-USER-INFO
              DFHPF12  ──► RETURN-TO-PREV-SCREEN (XCTL ← COADM01C)
              OTHER    ──► 오류 메시지 + SEND-USRDEL-SCREEN
            END-EVALUATE

  └─ EXEC CICS RETURN TRANSID('CU03') COMMAREA(...)
```

### PROCESS-ENTER-KEY (라인 142~169)

```
USRIDINI 비어 있음? → 에러 메시지 + SEND-USRDEL-SCREEN (종료)
아니면:
  FNAMEI / LNAMEI / USRTYPEI ← SPACES (화면 클리어)
  SEC-USR-ID ← USRIDINI
  PERFORM READ-USER-SEC-FILE
    ├─ NORMAL  → 화면에 "Press PF5 key to delete…" + SEND-USRDEL-SCREEN
    ├─ NOTFND  → 에러 + SEND-USRDEL-SCREEN
    └─ OTHER   → DISPLAY + 에러 + SEND-USRDEL-SCREEN
  (오류 없으면)
  FNAMEI ← SEC-USR-FNAME
  LNAMEI ← SEC-USR-LNAME
  USRTYPEI ← SEC-USR-TYPE
  PERFORM SEND-USRDEL-SCREEN
```

> 주의: `READ-USER-SEC-FILE` 내부에서 이미 `SEND-USRDEL-SCREEN`을 호출(라인 286)한 후, PROCESS-ENTER-KEY로 돌아와 ERR-FLG-ON이 아니면 다시 `SEND-USRDEL-SCREEN`을 호출(라인 168)한다. 즉 정상 경로에서는 SEND가 두 번 발생하는 구조이나, CICS SEND는 누적 전송이 아니라 마지막 호출이 터미널에 보이는 것이므로 기능적으로는 문제없다. 단, 현대화 시 이 중복 호출은 제거해야 한다.

### DELETE-USER-INFO (라인 174~192)

```
USRIDINI 비어 있음? → 에러 + SEND-USRDEL-SCREEN
아니면:
  SEC-USR-ID ← USRIDINI
  PERFORM READ-USER-SEC-FILE   (UPDATE 잠금 재획득)
  PERFORM DELETE-USER-SEC-FILE
    ├─ NORMAL → INITIALIZE-ALL-FIELDS + 성공 메시지(녹색) + SEND-USRDEL-SCREEN
    ├─ NOTFND → 에러 + SEND-USRDEL-SCREEN
    └─ OTHER  → DISPLAY + 에러 + SEND-USRDEL-SCREEN
```

### READ-USER-SEC-FILE (라인 267~300)

`EXEC CICS READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) UPDATE` — UPDATE 옵션으로 레코드 잠금을 획득한다. 이후 DELETE-USER-SEC-FILE이 해당 잠금을 이용해 삭제하므로 RIDFLD 재지정 없이도 동작한다(CICS는 직전 READ UPDATE 컨텍스트를 유지).

### DELETE-USER-SEC-FILE (라인 305~336)

`EXEC CICS DELETE DATASET('USRSEC')` — RIDFLD 없이 호출. 이는 직전 `READ … UPDATE`로 잠긴 레코드를 대상으로 한다. 성공 시 `STRING` 문으로 동적 메시지 조합: `"User " + SEC-USR-ID(공백까지) + " has been deleted ..."`.

---

## Java/현대화 노트

### 전체 구조 대응

```
COUSR03C (CICS pseudo-conversational)
 └─► Java: @Controller or @RestController + HttpSession (또는 JWT)

MAIN-PARA
 └─► @PostMapping("/users/delete") + 분기 로직

CARDDEMO-COMMAREA
 └─► HttpSession 속성 또는 stateless JWT payload

EXEC CICS RETURN TRANSID + COMMAREA
 └─► return "redirect:/users/delete" (세션 유지)

EXEC CICS XCTL PROGRAM(...)
 └─► return "redirect:/admin" (세션 전달 후 컨트롤러 종료)
```

### 데이터 구조 Java 매핑

| COBOL 필드 | PIC | Java 타입 | 비고 |
|---|---|---|---|
| `SEC-USR-ID` | X(8) | `String` (max 8) | KSDS 키, 공백 패딩 주의 |
| `SEC-USR-FNAME` | X(20) | `String` | 고정 길이 → trim() 필수 |
| `SEC-USR-LNAME` | X(20) | `String` | 동상 |
| `SEC-USR-PWD` | X(8) | `String` | 평문 저장됨 — 현대화 시 반드시 해시(BCrypt 등) 적용 |
| `SEC-USR-TYPE` | X(1) | `enum UserType { ADMIN('A'), USER('U') }` | 88-level 조건은 enum으로 |
| `SEC-USR-FILLER` | X(23) | 미사용 | 레코드 총 80바이트 맞춤용 |
| `WS-RESP-CD` | S9(9) COMP | `int` | CICS RESP 코드 (0=NORMAL) |

### 핵심 변환 주의사항

1. **READ UPDATE + DELETE 패턴 → 낙관적/비관적 잠금 선택 필요**
   COBOL은 `READ … UPDATE`로 레코드 잠금 후 같은 UOW 내에서 `DELETE`를 수행한다. Java JPA에서는 `@Version`(낙관적) 또는 `SELECT … FOR UPDATE`(비관적) 중 선택해야 한다. 관리자 전용 저빈도 화면이므로 낙관적 잠금으로 충분하다.

   ```java
   // 예시: Spring Data JPA 비관적 잠금
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   Optional<UserSecurity> findByUserId(String userId);
   ```

2. **2단계 확인(Enter 후 PF5) → 확인 대화상자(confirm dialog)**
   COBOL에서 Enter는 "조회", PF5는 "삭제 실행"으로 분리된 2단계이다. REST API 설계 시에는 GET /users/{id} 후 DELETE /users/{id} 의 2-call 흐름 또는 단일 DELETE에 확인 파라미터를 추가하는 방식으로 구현한다.

3. **SEND 중복 호출 제거**
   `READ-USER-SEC-FILE`이 정상 경로에서 `SEND-USRDEL-SCREEN`을 직접 호출하고(라인 286), 호출자 `PROCESS-ENTER-KEY`도 다시 `SEND-USRDEL-SCREEN`을 호출(라인 168)한다. Java 서비스 레이어로 분리할 때는 READ 성공 시 DTO를 반환하고 표시 로직은 컨트롤러 한 곳에서만 처리해야 한다.

4. **DFHBMSCA 색상 속성 → CSS/클라이언트 표현층으로 이전**
   `DFHGREEN`(성공), `DFHNEUTR`(중립)은 3270 터미널 색상 코드이다. Web UI에서는 CSS 클래스(`text-success`, `text-warning`)로 대체한다.

5. **고정 길이 문자열 처리**
   COBOL PIC X(n) 필드는 항상 공백 패딩된 고정 길이이다. Java에서는 VSAM을 직접 읽거나 레거시 인터페이스를 거칠 때 `String.trim()` 또는 Apache Commons `StringUtils.stripEnd(s, " ")` 를 반드시 적용해야 한다. 특히 `SEC-USR-ID`는 키로 사용되므로 비교 전 정규화가 중요하다.

6. **보안: 평문 패스워드**
   `SEC-USR-PWD` (PIC X(8))는 평문으로 VSAM에 저장된다. 현대화 시 BCrypt 해시 저장, 패스워드 최소 길이 정책 적용이 필수이다.

7. **XCTL vs CALL 구분**
   모든 화면 이동이 XCTL(제어 이전, 돌아오지 않음)이므로 Java에서는 서비스 메서드 호출(CALL 패턴)이 아닌 HTTP redirect로 대응해야 한다.

8. **COMMAREA 내 프로그램 간 선택 전달 (`CDEMO-CU03-USR-SELECTED`)**
   사용자 목록 화면이 선택된 User ID를 commarea에 담아 이 화면으로 XCTL하면, 첫 진입 시 자동으로 조회가 실행된다(라인 99~104). Java에서는 RedirectAttributes 또는 세션 플래시 속성(Flash scope)으로 구현한다.

   ```java
   // Spring MVC 예시
   redirectAttributes.addFlashAttribute("selectedUserId", userId);
   return "redirect:/users/delete";
   ```
