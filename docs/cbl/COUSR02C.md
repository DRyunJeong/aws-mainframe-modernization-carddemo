# COUSR02C — 사용자 정보 수정 화면

- **유형**: CICS 온라인 COBOL (Pseudo-conversational)
- **한 줄 요약**: 관리자가 USRSEC 파일에서 특정 사용자를 조회한 뒤 성명·비밀번호·유형을 수정·저장하는 사용자 편집 화면 프로그램.

---

## 기능 설명

COUSR02C는 CardDemo 관리자 메뉴에서 호출되는 사용자 계정 편집 화면이다. 트랜잭션 ID `CU02`로 등록되며, USRSEC VSAM KSDS 파일에 대해 CICS `READ ... UPDATE` + `REWRITE` 쌍으로 잠금-수정-저장 사이클을 수행한다.

주요 동작은 두 단계로 나뉜다.

1. **조회 단계**: 사용자가 User ID를 입력하고 Enter를 누르면 USRSEC 레코드를 `UPDATE` 모드로 읽어 현재 값을 화면에 채운다(READ 시 CICS 내부 락 획득). 성공하면 "Press PF5 key to save your updates ..." 안내 메시지를 표시한다.
2. **저장 단계**: PF5(또는 PF3) 키를 누르면 화면에서 받은 값과 파일 값을 필드별로 비교해 변경된 필드만 레코드에 반영 후 `REWRITE`로 커밋한다. 변경 사항이 없으면 저장을 수행하지 않고 "Please modify to update ..." 경고를 표시한다.

PF4는 화면 초기화, PF12는 관리자 메뉴(`COADM01C`)로 복귀, 그 외 키 입력은 오류 메시지를 띄운다.

---

## 입력 / 출력

- **입력**:
  - CICS COMMAREA(`DFHCOMMAREA`): 이전 프로그램이 전달한 `CARDDEMO-COMMAREA` 세션 상태. `EIBCALEN = 0`이면 인증 없이 직접 진입한 것으로 간주해 로그인 화면(`COSGN00C`)으로 리다이렉트한다.
  - 화면 입력 맵 `COUSR2AI` (MAPSET `COUSR02`, MAP `COUSR2A`): 사용자가 화면에 타이핑한 USRID(8자), FNAME(20자), LNAME(20자), PASSWD(8자), USRTYPE(1자).
  - VSAM KSDS 파일 `USRSEC`: `SEC-USR-ID`(8바이트)를 키로 READ UPDATE.
  - `CDEMO-CU02-USR-SELECTED`(COMMAREA 내): 이전 화면(사용자 목록 COUSR01C)이 넘겨준 선택 User ID. 첫 진입 시 자동으로 USRIDINI에 주입해 READ를 수행한다(라인 99–103).

- **출력**:
  - 화면 출력 맵 `COUSR2AO` (COUSR2AI를 REDEFINES): 헤더(제목·날짜·시간·트랜잭션 ID·프로그램명), 현재 사용자 데이터 필드, 오류 메시지(`ERRMSGO`, 색상 속성 `ERRMSGC` 포함).
  - VSAM KSDS 파일 `USRSEC`: REWRITE로 수정된 레코드 저장.
  - CICS COMMAREA: `EXEC CICS RETURN TRANSID('CU02') COMMAREA(CARDDEMO-COMMAREA)` 로 세션 상태를 다음 턴에 전달.

---

## 의존성

- **COPY (카피북)**:

  | 카피북 | 위치 | 역할 |
  |---|---|---|
  | `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | `CARDDEMO-COMMAREA` 01레벨 정의. 프로그램 간 세션 상태 객체. `CDEMO-PGM-REENTER`(88레벨), `CDEMO-FROM/TO-PROGRAM`, `CDEMO-USER-TYPE` 등 포함. |
  | `COUSR02` | `app/cpy-bms/COUSR02.CPY` | BMS 생성 symbolic map. `COUSR2AI`(입력구조)와 `COUSR2AO`(REDEFINES 출력구조). 각 필드마다 `L`(커서 길이), `F`(속성 플래그), `I`(입력값) / `C`(색상), `O`(출력값) 서브필드 포함. |
  | `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | 화면 상단 타이틀 상수(`CCDA-TITLE01`, `CCDA-TITLE02`). |
  | `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | `WS-CURDATE-DATA` 날짜/시간 분해 구조체. `FUNCTION CURRENT-DATE` 결과를 MM-DD-YY/HH-MM-SS로 분해. |
  | `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | 공통 오류 메시지 상수(`CCDA-MSG-INVALID-KEY` 등). |
  | `CSUSR01Y` | `app/cpy/CSUSR01Y.cpy` | `SEC-USER-DATA` 01레벨 정의. USRSEC 파일 레코드 레이아웃(총 80바이트). |
  | `DFHAID` | CICS 시스템 | `EIBAID` 비교용 상수(`DFHENTER`, `DFHPF3`, `DFHPF4`, `DFHPF5`, `DFHPF12`). |
  | `DFHBMSCA` | CICS 시스템 | 화면 속성 색상 상수(`DFHRED`, `DFHGREEN`, `DFHNEUTR`). |

  COUSR02C에는 `COCOM01Y` COPY 직후 WORKING-STORAGE에 인라인으로 `05 CDEMO-CU02-INFO` 그룹이 추가 선언되어 있다(라인 50–58). 이는 `CARDDEMO-COMMAREA`에 이어지는 프로그램 전용 확장 commarea 영역이다.

- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)`: 이전 화면 복귀 또는 관리자 메뉴로 제어 이전. 복귀 대상은 `CDEMO-FROM-PROGRAM` 값을 우선 사용하며, 비어 있으면 `COADM01C`로 이동. `EIBCALEN = 0`이면 `COSGN00C`으로 이동.
  - 서브프로그램 CALL 없음.

- **데이터셋/파일/DB 테이블**:
  - `USRSEC`: VSAM KSDS, 레코드 길이 80바이트. `SEC-USR-ID`(PIC X(8)) 기본 키. `READ ... UPDATE` (공유 락) + `REWRITE` 패턴. DB2 없음.

- **트랜잭션 ID 또는 EXEC PGM**:
  - CICS 트랜잭션 ID: `CU02`
  - JCL EXEC PGM: 해당 없음 (온라인 CICS 프로그램)

---

## 핵심 로직 흐름

```
MAIN-PARA
│
├─ [EIBCALEN = 0] ──→ CDEMO-TO-PROGRAM = 'COSGN00C'
│                      RETURN-TO-PREV-SCREEN (XCTL → COSGN00C)
│
└─ [EIBCALEN > 0]
    │  MOVE DFHCOMMAREA → CARDDEMO-COMMAREA
    │
    ├─ [NOT CDEMO-PGM-REENTER] ← 첫 진입 (context=0)
    │   SET CDEMO-PGM-REENTER = TRUE
    │   MOVE LOW-VALUES → COUSR2AO  ← 출력 맵 초기화
    │   MOVE -1 → USRIDINL  ← 커서를 USRID 필드에 위치
    │   ├─ [CDEMO-CU02-USR-SELECTED ≠ SPACES/LOW-VALUES]
    │   │   MOVE selected_id → USRIDINI
    │   │   PERFORM PROCESS-ENTER-KEY  ← 자동 조회
    │   └─ PERFORM SEND-USRUPD-SCREEN
    │
    └─ [CDEMO-PGM-REENTER] ← 재진입 (사용자 응답 처리)
        PERFORM RECEIVE-USRUPD-SCREEN
        EVALUATE EIBAID
        │
        ├─ DFHENTER → PROCESS-ENTER-KEY
        │              ├─ [USRIDINI 공백] → 오류 "User ID can NOT be empty"
        │              └─ [정상] MOVE USRIDINI → SEC-USR-ID
        │                         PERFORM READ-USER-SEC-FILE
        │                           ├─ DFHRESP(NORMAL): 필드 채움 + SEND
        │                           ├─ DFHRESP(NOTFND): 오류 "User ID NOT found"
        │                           └─ OTHER: 오류 "Unable to lookup User"
        │
        ├─ DFHPF3 → UPDATE-USER-INFO + RETURN-TO-PREV-SCREEN
        │
        ├─ DFHPF4 → CLEAR-CURRENT-SCREEN (INITIALIZE-ALL-FIELDS + SEND)
        │
        ├─ DFHPF5 → UPDATE-USER-INFO
        │            ├─ 필드 공백 검증 (USRID/FNAME/LNAME/PASSWD/USRTYPE)
        │            └─ [전체 통과]
        │                PERFORM READ-USER-SEC-FILE (UPDATE 락 재획득)
        │                필드별 변경 감지 → USR-MODIFIED-YES 플래그 설정
        │                ├─ [USR-MODIFIED-YES] PERFORM UPDATE-USER-SEC-FILE
        │                │    ├─ EXEC CICS REWRITE
        │                │    ├─ DFHRESP(NORMAL): 초록색 성공 메시지 + SEND
        │                │    ├─ DFHRESP(NOTFND): 오류 메시지
        │                │    └─ OTHER: 오류 메시지
        │                └─ [USR-MODIFIED-NO] "Please modify to update..." + SEND
        │
        ├─ DFHPF12 → CDEMO-TO-PROGRAM='COADM01C' + RETURN-TO-PREV-SCREEN
        │
        └─ OTHER → WS-ERR-FLG='Y', CCDA-MSG-INVALID-KEY + SEND
│
EXEC CICS RETURN TRANSID('CU02') COMMAREA(CARDDEMO-COMMAREA)
```

**주의: READ ... UPDATE와 REWRITE의 분리**

`READ-USER-SEC-FILE`은 `UPDATE` 옵션을 포함해 호출된다(라인 328). 이는 CICS가 해당 레코드에 대해 독점 락(exclusive enqueue)을 유지함을 의미한다. PROCESS-ENTER-KEY 경로에서는 조회 목적으로만 UPDATE 모드 READ를 수행하며, 이후 REWRITE를 하지 않는다. CICS 태스크가 `EXEC CICS RETURN`으로 종료되면 락이 자동 해제된다. 실제 REWRITE는 UPDATE-USER-INFO → UPDATE-USER-SEC-FILE에서만 수행된다. 즉, 같은 CICS 태스크 내에서 READ UPDATE 후 REWRITE가 이루어져야 하며, 서로 다른 태스크 턴에서 READ UPDATE와 REWRITE를 분리하면 CICS는 ILLOGIC 오류를 반환한다.

---

## Java/현대화 노트

### 전체 아키텍처 대응

| COBOL/CICS 구조 | Java/Spring 대응 |
|---|---|
| Pseudo-conversational RETURN | HTTP stateless request + 세션 쿠키/JWT |
| COMMAREA `CARDDEMO-COMMAREA` | `HttpSession` 또는 JWT 페이로드의 사용자 컨텍스트 DTO |
| `EIBCALEN = 0` 진입 가드 | Spring Security 인증 필터(`@PreAuthorize`) |
| `CDEMO-PGM-REENTER` 88레벨 플래그 | HTTP GET(초기 렌더링) vs HTTP POST(폼 제출) 구분 |
| EVALUATE EIBAID (키 분기) | 폼 액션 파라미터 또는 REST endpoint별 메서드 분리 |
| `EXEC CICS XCTL` | HTTP 302 리다이렉트 또는 Spring MVC `redirect:` |
| `EXEC CICS SEND/RECEIVE MAP` | Thymeleaf 뷰 렌더링 / `@RequestParam` 바인딩 |

### 데이터 구조 Java 매핑

`SEC-USER-DATA` (CSUSR01Y.cpy, 80바이트 고정 레코드):

```java
public class SecUserData {
    private String secUsrId;     // PIC X(8)  — 최대 8자 고정폭, 우측 SPACE 패딩
    private String secUsrFname;  // PIC X(20)
    private String secUsrLname;  // PIC X(20)
    private String secUsrPwd;    // PIC X(8)  — 평문 저장! 현대화 시 반드시 해시로 교체
    private String secUsrType;   // PIC X(1)  — 'A'=admin, 'U'=user (enum 변환 권장)
    // SEC-USR-FILLER PIC X(23): 미사용 패딩, Java에선 제거
}
```

### 필드별 주의사항

1. **비밀번호 평문 저장** (라인 169, 229): `SEC-USR-PWD PIC X(08)`에 비밀번호가 평문으로 저장·전송된다. Java 마이그레이션 시 BCrypt 등 단방향 해시로 반드시 교체해야 한다. 화면 전송 시에도 평문이 `PASSWDI`/`PASSWDO` 필드에 그대로 실린다.

2. **READ UPDATE 후 미 REWRITE 경로** (라인 322–339): `PROCESS-ENTER-KEY`에서 `READ ... UPDATE`로 락을 잡은 뒤 REWRITE 없이 `EXEC CICS RETURN`으로 태스크를 종료한다. CICS가 태스크 종료 시 자동으로 락을 해제하므로 교착 상태는 발생하지 않는다. Java/JPA로 전환 시 `@Transactional`로 감싼 서비스 메서드 안에서 조회와 저장을 단일 트랜잭션으로 처리해야 한다. 조회만 필요한 경우 `READ` 단독(UPDATE 없이)으로 전환해야 한다.

3. **변경 감지 로직** (라인 219–234): COBOL은 필드를 직접 문자열 비교(`NOT =`)로 변경 여부를 판단한다. 이는 JPA `@Entity`의 Dirty Checking이나 `equals()`/`hashCode()` 비교로 자연스럽게 대체된다.

4. **고정 길이 문자열 패딩**: COBOL `PIC X(n)` 필드는 항상 우측 스페이스로 패딩된다. Java `String.trim()` 또는 `StringUtils.trimTrailingWhitespace()`로 정규화 필요. USRSEC 레코드를 Java에서 직접 파싱할 경우 `EBCDIC`→`UTF-8` 변환도 고려해야 한다.

5. **커서 제어 필드** (`USRIDINL`, `FNAMEL` 등 `S9(4) COMP`): BMS 맵에서 `-1`을 설정하면 해당 필드에 커서가 위치된다. Java 웹 UI에서는 `autofocus` HTML 속성이나 JavaScript `element.focus()`로 구현한다.

6. **화면 색상 속성** (`ERRMSGC`에 `DFHRED`/`DFHGREEN`/`DFHNEUTR` 대입): 3270 터미널 속성 바이트. Java 웹 UI에서는 CSS 클래스(`alert-danger`, `alert-success`)로 매핑한다.

7. **CDEMO-CU02-INFO 인라인 확장 commarea** (라인 50–58): `COCOM01Y` COPY 직후 WORKING-STORAGE에 선언된 `05 CDEMO-CU02-INFO` 그룹은 commarea 구조 내 `CDEMO-MORE-INFO` 영역 이후에 이어지는 프로그램 전용 영역이다. Java 세션 DTO 설계 시 공통 세션 컨텍스트(공통 DTO)와 화면별 컨텍스트(서브클래스 또는 별도 세션 속성)를 분리하는 방식으로 대응할 수 있다.

8. **입력 유효성 검증 순서** (라인 179–213): EVALUATE TRUE 구문으로 USRID → FNAME → LNAME → PASSWD → USRTYPE 순서로 첫 번째 실패 조건에서 즉시 화면을 반환한다. 이는 Java Bean Validation의 `@NotBlank` + `BindingResult` 체크와 동일한 패턴이나, COBOL은 첫 번째 오류만 표시하는 early-exit 방식이다. Java에서는 모든 오류를 동시에 수집·표시할 수 있다(`BindingResult.hasErrors()`).
