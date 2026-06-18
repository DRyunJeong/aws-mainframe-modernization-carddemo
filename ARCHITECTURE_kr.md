# CardDemo 아키텍처 종합 참조 문서 (한국어)

> 대상 독자: **Java/OOP 배경의 현대 개발자** 중 메인프레임 경험이 거의 없는 분.
> 
> 목적: 여러 세션에 걸쳐 분석한 CardDemo의 구조를 하나의 참조 문서로 종합한다.
> 
> 표기 규칙: COBOL 키워드/식별자/Java 타입명은 원문 그대로 두고, 설명은 한국어로 쓴다.
> 
> 근거 표기: 가능한 한 `파일경로:라인` 형태로 근거를 달았다. **"(추측)"** 으로 표시한 부분은 소스로 완전히 검증하지 못한 추정이다.

---

## 목차

1. [개요](#1-개요)
2. [전체 아키텍처](#2-전체-아키텍처)
3. [온라인 계층 (CICS)](#3-온라인-계층-cics)
4. [배치 계층](#4-배치-계층)
5. [데이터 계층](#5-데이터-계층)
6. [선택 모듈 3개](#6-선택-모듈-3개)
7. [Java/Spring 마이그레이션 주의점](#7-javaspring-마이그레이션-주의점)
8. [부록 — 파일 경로 색인 / 미검증 영역](#8-부록--파일-경로-색인--미검증-영역)

---

## 1. 개요

### CardDemo란 무엇인가

CardDemo는 **신용카드 관리 업무**(계정·카드·고객·거래·이자계산·명세서·권한승인)를 다루는 가상의 메인프레임 애플리케이션이다. AWS가 공개한 **메인프레임 현대화(mainframe modernization) 도구 시연용 샘플**이며, 실제 상용 시스템이 아니다. README는 이 코드가 "마이그레이션, 리팩터링, 리플랫포밍, 증강(augmentation) 시나리오"를 테스트하기 위한 자원임을 명시한다 (`README.md:395`).

### 왜 이렇게 만들어졌는가 — 의도적인 스타일 혼합

CardDemo의 가장 중요한 특징은 **일부러 다양한(때로는 낡은) 메인프레임 관용구를 한곳에 모아 두었다**는 점이다. 현대화 도구가 얼마나 폭넓은 패턴을 변환할 수 있는지 보여주기 위해서다. 대표적으로 명세서 생성 프로그램 CBSTM03A는 헤더 주석에서 "이 프로그램은 모더나이제이션 연습을 위해 의도적으로 옛 관용구(ALTER, 제어블록 어드레싱, COMP/COMP-3, 2차원 배열, 서브루틴 CALL)를 모아 두었다"고 직접 밝힌다 (`app/cbl/CBSTM03A.CBL` 헤더 주석 30~35행).

따라서 이 코드베이스에서는 **깔끔한 패턴과 낡은 패턴이 의도적으로 공존**한다. 예를 들어:
- 잘 구조화된 pseudo-conversational CICS 프로그램 (CO* 다수)
- 반대로 `ALTER` + `GO TO`로 자기수정 상태머신을 짠 프로그램 (CBSTM03A)
- VSAM(KSDS) 위주의 베이스 + 선택적으로 얹는 DB2 / IMS 계층형 DB / MQ

이 "혼합"은 버그나 일관성 부족이 아니라 **설계 의도**다. 마이그레이션 시 각 패턴을 어떻게 다룰지 따로 판단해야 한다.

### 로컬 런타임이 없다

CardDemo는 **로컬에서 그냥 실행되지 않는다.** z/OS(또는 AWS Mainframe Modernization 같은 호환 런타임), CICS, VSAM, 그리고 선택 모듈을 쓸 경우 DB2 / IMS / MQ가 필요하다 (`README.md:98` "Optional: DB2, IMS DB, and MQ", `README.md:104~108` HLQ 정의·데이터셋 배포 절차). 즉 이 문서는 **소스를 읽고 이해/마이그레이션하기 위한 정적 분석 자료**이지, 로컬 구동 가이드가 아니다.

---

## 2. 전체 아키텍처

CardDemo는 전형적인 메인프레임 3계층 구조다: **온라인(대화형, CICS)** / **배치(야간 일괄)** / **데이터(VSAM 파일)**. 온라인과 배치는 **동일한 데이터 파일과 동일한 copybook(레코드 레이아웃)** 을 공유한다.

### 2.1 ASCII 아키텍처 다이어그램

```
                          ┌──────────────────────────────────────────┐
                          │              사용자 (3270 단말)             │
                          └───────────────────┬──────────────────────┘
                                              │ 화면 입출력 (BMS Map)
                ┌──────────────────────────────▼───────────────────────────────┐
                │                     온라인 계층 (CICS)                          │
                │  pseudo-conversational, 트랜잭션 단위 재기동                     │
                │                                                                │
                │   COSGN00C(로그인) → COADM01C(관리자메뉴) / COMEN01C(사용자메뉴)  │
                │        │ XCTL(제어 이양)                                        │
                │        ├── COACTVWC/COACTUPC  (계정 조회/수정)                   │
                │        ├── COCRDLIC/SLC/UPC   (카드 목록/상세/수정)              │
                │        ├── COTRN00C/01C/02C   (거래 목록/조회/추가)             │
                │        ├── COBIL00C           (청구/결제)                       │
                │        ├── CORPT00C           (리포트 요청)                     │
                │        └── COUSR00C/01C/02C/03C (사용자 관리)                    │
                └───────────────────────────────┬──────────────────────────────┘
                                                 │  EXEC CICS READ/WRITE/REWRITE
                                                 │  (+ STARTBR/READNEXT 페이징)
   ┌──────────────────────────┐    ┌────────────▼────────────────┐
   │      배치 계층 (JCL)        │    │      데이터 계층 (VSAM)        │
   │  CICS 미경유, 파일 직접 I/O  │    │     KSDS 위주 + 순차 파일      │
   │                          │───▶│                              │
   │  CBTRN02C  거래입력(posting)│ I/O│  ACCTDATA  계정 (CVACT01Y)   │
   │  CBACT04C  이자계산         │    │  CARDDATA  카드 (CVACT02Y)   │
   │  CBSTM03A/B 명세서 생성      │    │  CUSTDATA  고객 (CVCUS01Y)   │
   │  CBACT01~03C 마스터 갱신     │    │  CARDXREF  교차참조(CVACT03Y) │
   │  CBCUS01C  고객 처리        │    │  TRANSACT  거래 (CVTRA05Y)   │
   │  CBEXPORT/CBIMPORT 추출/적재 │    │  DALYTRAN  일일거래(CVTRA06Y) │
   │                          │    │  TCATBALF  카테고리잔액(CVTRA01Y)│
   │  (야간 시퀀스로 직렬 실행)    │    │  USRSEC    사용자보안(CSUSR01Y)│
   └──────────────────────────┘    └──────────────────────────────┘
                  ▲
                  │  CICS가 잡고 있는 VSAM을 배치 전후로 닫고/열기
                  └──  CLOSEFIL.jcl / OPENFIL.jcl (IEFBR14)

   ─────────────── 선택 모듈 (베이스에 "추가"로만 통합, 6장 참조) ───────────────
   app-transaction-type-db2 (DB2)  │  app-authorization-ims-db2-mq (IMS+DB2+MQ)  │  app-vsam-mq (MQ)
```

### 2.2 계층 간 상호작용 핵심

| 상호작용 | 메커니즘 | 비고 |
|---|---|---|
| 단말 ↔ 온라인 | BMS Map(화면) + CICS 트랜잭션 | 요청마다 트랜잭션 재기동 (3장) |
| 온라인 ↔ 데이터 | `EXEC CICS READ/WRITE/REWRITE/DELETE`, 브라우즈(`STARTBR/READNEXT`) | CICS가 파일 관리자 역할 |
| 온라인 ↔ 온라인 | `EXEC CICS XCTL`(제어 이양), `LINK`(호출 후 복귀) | 프로그램 간 내비게이션 |
| 배치 ↔ 데이터 | `FILE-CONTROL` `SELECT ... ASSIGN` 으로 파일 직접 OPEN/READ | CICS 미경유, JCL DD명과 결합 |
| 배치 직렬 실행 | JCL 잡 스트림 (`scripts/run_full_batch.sh`) | 야간 일괄 (4장) |
| 배치 ↔ 온라인 자원 충돌 회피 | `CLOSEFIL.jcl`/`OPENFIL.jcl` 로 CICS의 VSAM을 닫고/열기 | 동시 접근 충돌 방지 |

### 2.3 CardDemo ↔ Java/Spring 대응표 (한눈에)

| CardDemo (메인프레임) | Java / Spring 대응 | 보충 |
|---|---|---|
| CICS 온라인 트랜잭션 | Spring MVC Controller + 무상태 HTTP 요청 | pseudo-conversational ↔ stateless 요청 |
| COMMAREA (프로그램 간 전달 영역) | HTTP Session / 직렬화된 세션 토큰 / 요청 DTO | `COCOM01Y` = `CARDDEMO-COMMAREA` |
| `EXEC CICS XCTL` | HTTP 302 redirect + 세션 전달, 또는 다른 컨트롤러로 forward | 제어를 넘기고 돌아오지 않음 |
| `EXEC CICS LINK` | 동기 메서드/서비스 호출 (호출 후 복귀) | 같은 UOW 안 |
| BMS Map (화면) + symbolic map copybook | Thymeleaf/JSP 뷰 + 폼 바인딩 DTO | `app/bms/*.bms` → `app/cpy-bms/*.CPY` |
| 배치 프로그램(CB*) + JCL | Spring Batch Job/Step + 스케줄러 | 4장 |
| VSAM KSDS | 인덱스 키 기반 테이블/리포지토리 (RDB의 PK 테이블) | 5장 |
| copybook (레코드 레이아웃) | 공유 DTO / 엔티티 클래스 | `app/cpy/*.cpy` |
| `EXEC SQL` (DB2) | JDBC / JPA | 선택 모듈 (6장) |
| `EXEC DLI` (IMS 계층형 DB) | 부모-자식 aggregate 탐색 (navigational) | 직접 대응 약함 (6장) |
| MQ(`MQGET/MQPUT`) | JMS / `@JmsListener` / MDB | 비동기 request/reply (6장) |
| CICS 트랜잭션 코디네이터 (`SYNCPOINT`) | JTA `TransactionManager` / `@Transactional` | 2PC (6장) |

---

## 3. 온라인 계층 (CICS)

근거: 메모리 [online-pseudoconv-pattern], `app/cbl/CO*.cbl`.

### 3.1 pseudo-conversational 모델 (가장 중요한 개념)

CICS 온라인 프로그램은 **사용자 한 번의 화면 응답을 처리할 때마다 트랜잭션이 새로 기동되고, 화면을 보낸 뒤 즉시 종료**한다. 사용자가 다음 키를 누르면 같은 트랜잭션이 다시 기동된다. **두 번의 응답 사이에는 메모리에 아무 상태도 남지 않는다.**

> 이것은 **HTTP의 무상태 요청 모델과 사실상 동형**이다. 매 요청은 독립적이고, 상태는 직렬화된 토큰(여기서는 COMMAREA)으로 다음 요청에 전달된다.

```
[사용자 키 입력]
   │
   ▼
트랜잭션 기동 ──▶ 프로그램 시작
   │
   ├─ IF EIBCALEN = 0  → 최초 진입(상태 없음): 화면 첫 전송
   │
   └─ ELSE → COMMAREA 복원 → 처음 그리기 vs 사용자 응답 처리 구분
              │
              ├─ EVALUATE EIBAID  (어떤 키를 눌렀나: ENTER / PF3 / ...)
              │
              ▼
         EXEC CICS SEND MAP  (화면 출력)
              │
              ▼
         EXEC CICS RETURN TRANSID(...) COMMAREA(...)  ← 종료, 상태는 COMMAREA로 보존
   │
   ▼
[메모리 비워짐 — 다음 키 입력까지 아무것도 안 남음]
```

### 3.2 표준 골격 (모든 CO* 프로그램 공통)

이 구조를 알면 어떤 CO* 화면을 읽든 흐름을 예측할 수 있다 (메모리 [online-pseudoconv-pattern]):

1. **WORKING-STORAGE**: `WS-PGMNAME`, `WS-TRANID` 리터럴. `COPY COCOM01Y`(commarea), 화면 copybook, `COPY DFHAID`(EIBAID 키 상수: `DFHENTER`/`DFHPF3` 등), `COPY DFHBMSCA`(화면 속성).
2. **LINKAGE SECTION**: `01 DFHCOMMAREA` 를 `PIC X OCCURS 1 TO 32767 DEPENDING ON EIBCALEN` 로 수신 (예: `COSGN00C.cbl:67`).
3. **MAIN 분기**: `IF EIBCALEN = 0`(최초 진입) → 화면 첫 전송 / signon. `ELSE` → `MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA` 후 `CDEMO-PGM-REENTER`(88-level)로 "처음 그리기" vs "응답 처리" 구분, 응답 처리 시 `EVALUATE EIBAID` 로 키 분기 (예: `COSGN00C.cbl:80`, `:85`).
4. **매 턴 종료**: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)`.

### 3.3 EIBCALEN / EIBAID / COMMAREA

| 메인프레임 개념 | 의미 | Java 대응 |
|---|---|---|
| **EIBCALEN** | 넘어온 COMMAREA의 길이. `0`이면 "이번이 최초 진입(이전 상태 없음)" | `session == null` 또는 첫 요청 판정 |
| **EIBAID** | 사용자가 누른 Attention ID(어떤 키: ENTER, PF3, PF5…) | HTTP 메서드/버튼 name, 폼 액션 |
| **COMMAREA** | 트랜잭션 간 전달되는 상태 버퍼(`CARDDEMO-COMMAREA`, copybook `COCOM01Y`) | 세션 객체 / 직렬화 토큰 |
| **DFHCOMMAREA** | LINKAGE에서 COMMAREA를 받는 표준 이름 | 컨트롤러 메서드의 세션 파라미터 |

`CARDDEMO-COMMAREA`(=`COCOM01Y`) 주요 필드 (메모리 [copybook-record-layouts]):
- `CDEMO-FROM-PROGRAM` / `CDEMO-TO-PROGRAM` — 화면 간 이동 추적
- `CDEMO-USER-TYPE` (88: `CDEMO-USRTYP-ADMIN = 'A'`) — 권한 분기
- `CDEMO-PGM-CONTEXT` (88: `CDEMO-PGM-REENTER`) — 처음 그리기 vs 재진입 구분

### 3.4 CO* 네이밍 규칙과 화면 목록

`CO` = **C**ICS **O**nline. 베이스 온라인 프로그램은 18개다 (`app/cbl/CO*.cbl`):

| 프로그램 | 기능(추정 포함) |
|---|---|
| COSGN00C | 로그인(Sign-on) |
| COADM01C | 관리자 메뉴 |
| COMEN01C | 일반 사용자 메뉴 |
| COACTVWC / COACTUPC | 계정 조회 / 수정 |
| COCRDLIC / COCRDSLC / COCRDUPC | 카드 목록 / 상세 / 수정 |
| COTRN00C / COTRN01C / COTRN02C | 거래 목록 / 조회 / 추가 |
| COBIL00C | 청구/결제(Bill Pay) |
| CORPT00C | 리포트 요청 |
| COUSR00C / COUSR01C / COUSR02C / COUSR03C | 사용자 목록/추가/수정/삭제 |
| COBSWAIT | (배치/대기 보조, 추정) |

### 3.5 웹 요청/응답·세션 매핑표

| CICS 구문 | Java/Spring 웹 대응 |
|---|---|
| 트랜잭션 1회 기동 + `RETURN` | HTTP 요청 1건 처리 + 응답 |
| `EXEC CICS RECEIVE MAP(...)` | 폼 데이터 바인딩 (`@ModelAttribute`) |
| `EXEC CICS SEND MAP(...) ERASE` | 뷰 렌더링 (Thymeleaf/JSP) |
| `EVALUATE EIBAID`(ENTER/PF3…) | 버튼/액션 분기 |
| `RETURN TRANSID(...) COMMAREA(...)` | 응답 + 세션 갱신 |
| `EXEC CICS XCTL PROGRAM('...')` | redirect / forward (제어 이양) |
| `EXEC CICS LINK PROGRAM('...')` | 서비스 메서드 호출 (복귀) |
| 브라우즈 `STARTBR/READNEXT/READPREV/ENDBR` | 커서 기반 페이지네이션 |

### 3.6 메뉴 디스패치 = 데이터 주도 라우팅

COMEN01C / COADM01C는 메뉴 옵션 테이블(`COMEN02Y` / `COADM02Y`)의 `CDEMO-MENU-OPT-PGMNAME`(선택 번호에 대응하는 프로그램명)을 **그대로 XCTL 대상 프로그램명으로 사용**한다. 즉 if-else 하드코딩이 아니라 테이블 룩업 기반 라우팅이다(= Java의 `Map<옵션번호, 핸들러>` 디스패처). COMEN01C는 설치되지 않은 옵션 모듈(예: `COPAUS0C`)을 `EXEC CICS INQUIRE PROGRAM ... NOHANDLE`로 탐지해 "not installed"로 처리한다 (메모리 [online-pseudoconv-pattern]).

### 3.7 예시: COSGN00C 로그인 흐름

근거: `app/cbl/COSGN00C.cbl`.

```
COSGN00C (트랜잭션: 로그인 화면)
   │
   ├─ IF EIBCALEN = 0 (최초 진입)               ............. :80
   │      EVALUATE EIBAID → 로그인 화면 첫 전송   ............. :85
   │
   └─ ELSE (사용자가 ID/PW 입력 후 응답)
          │
          ├─ 입력 검증: ID/PW 비었나 EVALUATE TRUE .......... :117~130
          │     (비어 있으면 "Please enter ..." 메시지)
          │
          ├─ EXEC CICS READ DATASET(WS-USRSEC-FILE)         . :212
          │     (USRSEC = 사용자보안 VSAM, copybook CSUSR01Y)
          │
          └─ EVALUATE WS-RESP-CD                            . :221
                ├─ 정상(레코드 찾음):
                │     IF SEC-USR-PWD = WS-USER-PWD          . :223  ← 평문 비교!
                │        ├─ MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE . :227
                │        ├─ IF 관리자 → XCTL PROGRAM('COADM01C') . :231~232
                │        └─ ELSE     → XCTL PROGRAM('COMEN01C') . :236~237
                │     ELSE → "Wrong Password. Try again ..." . :242
                └─ 레코드 없음 → 사용자 없음 메시지
```

핵심 관찰:
- **사용자 인증 = VSAM 파일(USRSEC) 직접 조회**. 별도 인증 서버가 아니다.
- **비밀번호를 평문으로 비교**한다 (`:223` `SEC-USR-PWD = WS-USER-PWD`). 마이그레이션 시 반드시 해시+솔트로 교체해야 한다 (7장 보안 항목).
- 인증 성공 후 **사용자 타입(A=admin / U=user)에 따라 XCTL로 다른 메뉴 프로그램으로 분기**. COMMAREA에 `CDEMO-USER-TYPE`을 실어 보내 이후 화면이 권한을 알 수 있게 한다.
- USRSEC 레코드(`CSUSR01Y` = `SEC-USER-DATA`)에는 `SEC-USR-ID` / `SEC-USR-PWD` / `SEC-USR-TYPE`('A'=admin, 'U'=user)이 있다 (메모리 [copybook-record-layouts]).

Java 대응 개념:
```java
// 개념 스케치 (실제 코드 아님)
SecUser user = userSecRepository.findById(inputId);     // EXEC CICS READ USRSEC
if (user == null) return "user not found";
if (!user.getPwd().equals(inputPwd)) return "wrong pw"; // ← 평문 비교, 실제론 passwordEncoder.matches()
session.setUserType(user.getType());                    // COMMAREA에 타입 적재
return user.isAdmin() ? "redirect:/admin/menu"          // XCTL COADM01C
                      : "redirect:/user/menu";          // XCTL COMEN01C
```

---

## 4. 배치 계층

근거: 메모리 [batch-and-optional-modules], [statement-generation-cbstm03], `app/cbl/CB*.cbl`, `app/jcl/*`.

### 4.1 CB* 네이밍과 표준 배치 골격

`CB` = **C**OBOL **B**atch. 배치 프로그램은 **CICS를 거치지 않고** `FILE-CONTROL`의 `SELECT ... ASSIGN`으로 VSAM/순차 파일을 **직접** 연다. JCL의 DD명이 `ASSIGN` 명과 연결된다(예: `POSTTRAN.jcl`의 `//DALYTRAN DD ...` ↔ `ASSIGN TO DALYTRAN`).

표준 골격 (메모리 [batch-and-optional-modules]):
```
0000~0500  OPEN 단락들
   │
   ▼
PERFORM UNTIL END-OF-FILE = 'Y'   ← GET-NEXT 루프 (= ItemReader 순회)
   │  각 I/O 후 FILE STATUS 2바이트 검사
   │     '00' 정상 / '10' EOF / 그 외 오류
   │
   ▼
9000~      CLOSE 단락들
   │
   ▼
GOBACK
```
오류 시 `9999-ABEND-PROGRAM`이 `CALL 'CEE3ABD'`로 강제 abend. reject 발생 시 `RETURN-CODE 4` 설정.

배치 프로그램 목록 (`app/cbl/CB*.cbl`):

| 프로그램 | 기능 |
|---|---|
| CBACT01C / 02C / 03C | 계정/카드/교차참조 마스터 처리 (추정: 조회·갱신) |
| CBACT04C | **이자 계산** (4.4 참조) |
| CBTRN01C / 02C / 03C | 거래 처리 (02C = 거래입력 posting, 4.3 참조) |
| CBCUS01C | 고객 처리 |
| CBSTM03A / CBSTM03B | **명세서 생성** (드라이버 + I/O 서브루틴, 4.5 참조) |
| CBEXPORT / CBIMPORT | 데이터 추출 / 적재 (최근 커밋에서 추가됨) |

### 4.2 JCL ↔ 프로그램 관계, 야간 배치 시퀀스

JCL은 **무엇을 어떤 순서로, 어떤 파일(DD)을 붙여 실행할지** 기술하는 잡 제어 언어다. Spring 관점에서는 **Job 정의 + 스케줄 + 리소스 바인딩**에 해당한다.

야간 배치 시퀀스 (`scripts/run_full_batch.sh`, 메모리 [batch-and-optional-modules]):
```
마스터 갱신 (ACCTFILE/CARDFILE/XREFFILE/CUSTFILE ...)
   │
   ▼  POSTTRAN  → CBTRN02C  (일일거래 입력/posting)
   │
   ▼  INTCALC   → CBACT04C  (이자 계산)
   │
   ▼  TRANBKP             (거래 백업)
   │
   ▼  COMBTRAN  (SORT)    (거래 병합/정렬)
   │
   ▼  TRANIDX             (AIX = 대체 인덱스 정의)
   │
   ▼  OPENFIL             (CICS에 파일 재오픈)

[CLOSEFIL/OPENFIL] = CICS가 잡고 있는 VSAM을 배치 전후로 닫고/여는 IEFBR14 잡
```

> **마이그레이션 시사점**: 이 직렬 잡 스트림 전체가 하나의 Spring Batch `Job`(여러 `Step`을 `next()`로 연결)으로 표현될 수 있다. CLOSEFIL/OPENFIL 같은 "리소스 락 회피" 단계는 RDB 환경에서는 대개 불필요해진다(동시성은 DB 트랜잭션/락으로 처리).

### 4.3 CBTRN02C — 거래 입력(posting), Spring Batch 청크 대응

근거: 메모리 [batch-and-optional-modules], job POSTTRAN.

6개 파일을 사용한다:

| DD/파일 | 모드 | 역할 |
|---|---|---|
| DALYTRAN | 입력 순차 | 일일 거래 (드라이빙) |
| TRANSACT | 출력 KSDS | 확정 거래 기록 |
| CARDXREF | 랜덤 | 카드→계정 조회 (교차참조) |
| DALYREJS | 출력 순차 | 거절된 거래 기록 |
| ACCTDATA | I-O 랜덤 | 계정 잔액 갱신 |
| TCATBALF | I-O 랜덤 | 거래 카테고리 잔액 갱신 |

각 일일거래 1건마다:
```
1500-VALIDATE-TRAN
   ├─ CARDXREF로 카드번호 → 계정 조회
   ├─ 신용한도 검증
   └─ 카드 만료일 검증
        │
        ├─ 통과 → 2000-POST-TRANSACTION
        │           ├─ 카테고리 잔액 갱신(없으면 생성)
        │           ├─ 계정 잔액 / 사이클 차변·대변 갱신
        │           └─ TRANSACT에 확정 거래 기록
        │
        └─ 실패 → DALYREJS에 거절코드와 함께 기록
```

> **Spring Batch 대응**: DALYTRAN 읽기 = `ItemReader`, 검증+posting = `ItemProcessor`, TRANSACT/DALYREJS 쓰기 = `ItemWriter`(또는 분기 라이터), 커밋 간격 = chunk size. ACCTDATA/TCATBALF는 처리 중 갱신하는 보조 리포지토리.

### 4.4 CBACT04C — 이자 계산, control-break

근거: 메모리 [batch-and-optional-modules], job INTCALC, `app/jcl/INTCALC.jcl` STEP15.

진입 방식: `PARM='YYYYMMDD0'`(10바이트)로 처리일자를 받아 LINKAGE의 `EXTERNAL-PARMS`(`PARM-LENGTH S9(4) COMP` + `PARM-DATE X(10)`)로 수신. **JCL PARM → COBOL 진입의 표준 패턴**이다.

입력 5파일: TCATBALF(거래 카테고리 잔액 KSDS, **ACCESS SEQUENTIAL = 드라이빙 파일**), XREFFILE(카드↔계정, AIX로 ACCT-ID 조회), ACCTFILE(I-O, 잔액 갱신), DISCGRP(디스클로저 그룹 = 이자율 마스터), TRANSACT(OUTPUT 순차 = 생성된 이자거래).

**control-break 패턴** (정렬된 입력에서 키가 바뀌는 경계마다 집계를 마무리하는 고전 배치 기법):
```
TCATBALF를 계정ID 오름차순으로 순차 스캔
   │
   ├─ TRANCAT-ACCT-ID가 직전(WS-LAST-ACCT-NUM)과 다르면 = control break
   │      1050-UPDATE-ACCOUNT:
   │         ├─ 누적이자 WS-TOTAL-INT를 ACCT-CURR-BAL에 가산
   │         ├─ 사이클 차변/대변 0으로 리셋
   │         └─ ACCTFILE REWRITE
   │      그 후 새 계정의 ACCT/XREF를 읽어둠
   │
   └─ 각 카테고리 행마다:
          DISCGRP에서 이자율 조회
            (없으면 status '23' → 그룹ID 'DEFAULT'로 재조회 = fallback)
          1300-COMPUTE-INTEREST:
             COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
             → 월이자 = 잔액 × 연이자율% / 1200  (= /100/12)
          1300-B-WRITE-TX:
             이자거래를 TRANSACT에 WRITE
               (TRAN-ID = STRING PARM-DATE + 6자리 시퀀스, TYPE='01'/CAT='05',
                TRAN-SOURCE='System', DB2 형식 타임스탬프)
```

데이터 형식 주의:
- `DIS-INT-RATE` = `PIC S9(4)V99` (연이자율 %, 예: `12.50`)
- `TRAN-CAT-BAL` / `WS-MONTHLY-INT` / `WS-TOTAL-INT` = `S9(9)V99`
- → Java `BigDecimal`, scale=2. **나눗셈은 `RoundingMode` 명시 필수** (COBOL `COMPUTE` 기본은 truncate).
- `1400-COMPUTE-FEES`는 `EXIT`만 있는 **미구현 스텁**.

> **Spring Batch 대응**: TCATBALF = `ItemReader`, control-break = 계정 단위 청크 경계(또는 그룹 집계), 이자거래 WRITE = `ItemWriter`, ACCTFILE REWRITE = 별도 업데이트.

### 4.5 CBSTM03A / CBSTM03B — 명세서 생성 (ALTER/GO TO·2차원 조인)

근거: 메모리 [statement-generation-cbstm03], job CREASTMT (`app/jcl/CREASTMT.JCL`).

이 한 쌍은 **현대화 도구 연습을 위해 일부러 옛 관용구를 모아 둔** 프로그램이다(헤더 주석 30~35행).

**역할 분담**:

| 프로그램 | 역할 |
|---|---|
| CBSTM03A.CBL | 드라이버/포매터. 비즈니스 로직 + **텍스트(STMTFILE, 80바이트)** & **HTML(HTMLFILE, 100바이트)** 두 출력 작성. 자기는 STMTFILE/HTMLFILE만 직접 OPEN |
| CBSTM03B.CBL | 범용 파일 핸들러 서브루틴. TRNXFILE/XREFFILE/CUSTFILE/ACCTFILE 4개 VSAM을 대신 OPEN/READ/CLOSE |

**CALL 규약** (= Java의 단일 DAO 파사드 + 호출측 역직렬화): 공유 인터페이스 `WS-M03B-AREA`(03A) ↔ `LK-M03B-AREA`(03B), 레이아웃 = DD명 `X(8)` + OPER `X(1)`(88: O/C/R/K/W/Z) + RC `X(2)` + KEY `X(25)` + KEY-LN `S9(4)` + FLDT `X(1000)`. 03A가 DD명+오퍼레이션을 세팅 후 `CALL 'CBSTM03B' USING WS-M03B-AREA` → 03B가 `EVALUATE`로 파일·오퍼레이션 분기, 결과를 파일상태 2바이트로 RC에 반환, 레코드는 범용 버퍼 FLDT(`X(1000)`)에 담아 돌려줌. 03A는 FLDT를 해당 copybook 01레벨로 MOVE해 해석.

개념 대응:
```java
byte[] io(String ddName, char op, String key);  // CBSTM03B 파사드
// 호출측(03A)이 반환 byte[]를 적절한 레코드 DTO로 역직렬화
```

**비자명 관용구 — ALTER + GO TO 디스패처** (`CBSTM03A` `0000-START`, 296행~):
```
WS-FL-DD 값에 따라:
    ALTER 8100-FILE-OPEN TO PROCEED TO 8xxx-...-OPEN   ← GO TO 타깃을 런타임에 바꿔치기(자기수정 코드!)
    GO TO 8100-FILE-OPEN
각 OPEN 단락 끝:
    WS-FL-DD를 다음 단계로 바꾸고
    GO TO 0000-START  ← 상태머신을 GO TO로 회전
흐름: TRNXFILE open → READTRNX(전체 거래를 메모리 테이블에 적재)
      → XREFFILE → CUSTFILE → ACCTFILE → 1000-MAINLINE
```
> **Java 매핑: ALTER/GO TO는 직접 대응이 없다.** `switch` 기반 명시적 상태머신 또는 순차 메서드 호출로 평탄화해야 한다. `ALTER`는 거의 모든 현대 표준/린터가 금지한다(추적 불가). **절대 그대로 옮기지 말 것.**

**파일 조인 = 메모리 내 2차원 테이블 (nested loop join)**:
```
8500-READTRNX: TRNXFILE(카드번호로 정렬) 전체를 WS-TRNX-TABLE에 적재
   구조: WS-CARD-TBL OCCURS 51 { WS-CARD-NUM + WS-TRAN-TBL OCCURS 10 }
         → [카드][거래] 2차원  (= Map<CardNum, List<Tran>> 또는 Tran[51][10])

1000-MAINLINE: XREFFILE(카드↔고객↔계정 교차참조)을 순차로 돌며
   각 건마다 CUSTFILE/ACCTFILE을 키로 GET(03B 경유)
   → 5000-CREATE-STATEMENT (헤더/고객/계정 라인)
   → 4000-TRNXFILE-GET: 적재된 2차원 테이블에서 해당 카드의 거래들을
        중첩 루프(PERFORM VARYING CR-JMP/TR-JMP)로 찾아 본문 출력, 합계 산출
   = XREF 드라이빙 + 메모리 테이블 조인
```

**출력 2종 동시 생성**:
- 텍스트(STMTFILE): 고정폭 라인. 금액 편집필드 `PIC Z(9).99-`(Z=zero suppress, 후행 `-`=음수부호).
- HTML(HTMLFILE): 88레벨 상수로 고정 태그를 정의하고 `SET HTML-Lxx TO TRUE` 후 WRITE, 가변부는 STRING으로 `<p>...</p>` 조립. **템플릿 없이 코드로 HTML 직접 생성** (현대라면 Thymeleaf/JSP로 대체).

데이터 gotcha: 금액 `WS-TOTAL-AMT S9(9)V99` = COMP-3 → `BigDecimal` scale 2. 카운터 `S9(4)` COMP = `short`/`int`. OCCURS 상한(51카드/10거래)은 **하드코딩이며 경계검사 없음** → 초과 시 테이블 오버런. Java `List`로 풀면 제약은 사라지지만 **원본 데이터 가정(카드당 최대 10건 등)을 반드시 확인**해야 한다. CBSTM03A는 PSA/TCB/TIOT 제어블록 어드레싱(`SET ADDRESS OF`, POINTER, 240행~)으로 실행 중 job명/DD명을 읽는데, 이는 z/OS 내부구조 의존 — **Java엔 대응이 없다**(환경변수/설정으로 대체).

---

## 5. 데이터 계층

근거: 메모리 [copybook-record-layouts], `app/cpy/*.cpy`.

### 5.1 VSAM KSDS

CardDemo 데이터는 대부분 **VSAM KSDS(Key-Sequenced Data Set)** 다. KSDS는 **키 순으로 정렬되고 키로 직접 접근 가능한 인덱스 파일**로, 개념적으로 RDB의 "PK가 있는 단일 테이블"에 가깝다.

| RDB/Java 개념 | VSAM KSDS 대응 |
|---|---|
| 기본키(PK) 조회 | 키로 직접 READ |
| 보조 인덱스 | AIX (Alternate Index) — 예: XREFFILE을 ACCT-ID로 |
| 커서 페이지네이션 | `STARTBR` → `READNEXT`/`READPREV` → `ENDBR` |
| INSERT/UPDATE/DELETE | `WRITE` / `REWRITE` / `DELETE` |
| 행(row) | 고정길이 레코드 |
| 컬럼(column) | copybook의 PIC 필드 |

### 5.2 copybook = 공유 DTO / 스키마

CardDemo의 모든 VSAM 레코드 레이아웃은 `app/cpy/`의 **copybook**으로 정의되며, **온라인(CICS)과 배치가 동일 copybook을 공유**한다. 이것이 Java의 **공유 DTO/엔티티 클래스**에 해당한다. 한 copybook을 고치면 그 레코드를 쓰는 모든 프로그램에 반영된다.

### 5.3 주요 레코드 레이아웃 매핑표

근거: 메모리 [copybook-record-layouts].

| 파일 (RECLN) | copybook | 01 레벨명 | 키 / 비고 |
|---|---|---|---|
| ACCTDATA (300) | CVACT01Y | `ACCOUNT-RECORD` | `ACCT-ID PIC 9(11)` 키 |
| CARDDATA (150) | CVACT02Y | `CARD-RECORD` | 카드 |
| CUSTDATA (500) | CVCUS01Y | (고객 레코드) | 고객 |
| CARDXREF (50) | CVACT03Y | `CARD-XREF-RECORD` | `XREF-CARD-NUM`, `XREF-ACCT-ID`, `XREF-CUST-ID` (카드↔계정↔고객 교차참조) |
| TRANSACT (350) | CVTRA05Y | `TRAN-RECORD` | 온라인 거래 KSDS |
| DALYTRAN (350) | CVTRA06Y | `DALYTRAN-RECORD` | TRAN-RECORD과 구조 동일, 접두사만 `DALYTRAN-` |
| TCATBALF (50) | CVTRA01Y | `TRAN-CAT-BAL-RECORD` | 키: ACCT-ID + TYPE-CD + CAT-CD |
| TRANCATG (60) | CVTRA04Y | (거래 카테고리) | 코드 마스터 |
| TRANTYPE (60) | CVTRA03Y | (거래 유형) | 코드 마스터 |
| DISCGRP (50) | CVTRA02Y | (공시그룹) | 이자율 마스터 |
| USRSEC (80) | CSUSR01Y | `SEC-USER-DATA` | `SEC-USR-ID/PWD/TYPE` ('A'=admin,'U'=user) |

공용 인프라 copybook:
- `COCOM01Y` = `CARDDEMO-COMMAREA` (프로그램 간 commarea / 세션 상태) — 3.3 참조
- `COMEN02Y` / `COADM02Y` = 사용자/관리자 메뉴 옵션 테이블 — 3.6 참조

화면(symbolic map) copybook: BMS 소스(`app/bms/*.bms`)에서 생성된 `app/cpy-bms/*.CPY`. 각 화면 필드 `FOO`마다 입력구조(`...I`)에 `FOOL`(길이/커서 `S9(4) COMP`), `FOOF`(속성/플래그), `FOOI`(입력값), 출력구조(`...O`, REDEFINES)에 `FOOC/FOOP/FOOH/FOOV`(색상/속성)와 `FOOO`(출력값)가 생성된다. 예: `COSGN00.bms` → `COSGN00.CPY`의 `COSGN0AI`/`COSGN0AO`.

### 5.4 COBOL 데이터 형식 정리 (Java 매핑)

| COBOL 구문 | 의미 | 저장형 | Java 대응 | 주의 |
|---|---|---|---|---|
| `PIC X(n)` | 고정길이 문자 | DISPLAY (EBCDIC) | `String`(고정폭) | 공백 패딩, trim 필요 |
| `PIC 9(n)` | 무부호 숫자(문자형) | DISPLAY | `int`/`long`/`String` | 각 자리=1바이트 |
| `PIC S9(n)` | 부호 숫자 | DISPLAY | `int`/`long` | 부호는 마지막 바이트에 overpunch |
| `PIC S9(p)V9(s) COMP-3` | 압축 십진(packed decimal) | COMP-3 (니블당 1자리 + 부호) | **`BigDecimal`** scale=s | **부동소수점 금지**, 정밀도 보존 |
| `PIC S9(n) COMP` | 이진 정수 | COMP (2/4/8바이트 binary) | `short`/`int`/`long` | 바이트 크기는 자릿수 의존 |
| `V` (implied decimal) | 소수점 위치(저장 안 됨) | — | `BigDecimal`의 scale | 실제 점 문자 없음 — 오프셋만 |
| **88-level** (condition name) | 특정 값에 붙인 boolean 이름 | — | boolean 술어 / `enum` | 예: `88 CDEMO-USRTYP-ADMIN VALUE 'A'` → `isAdmin()` |
| **REDEFINES** | 같은 메모리를 다른 레이아웃으로 겹쳐 봄 | — | **직접 대응 없음** | union/오버레이. ByteBuffer 뷰 또는 두 클래스 + 변환으로 풀기 |
| **OCCURS n** | 배열(반복) | 연속 메모리 | `T[]` / `List<T>` | 1-based 인덱스, 경계검사 없음 |
| `OCCURS ... DEPENDING ON` | 가변 길이 배열 | — | `List<T>` | 길이 필드와 연동 |
| **COPY** | copybook 포함 | — | 공유 DTO / import | 5.2 |

> **핵심 함정**: 금액·잔액·이자율은 거의 모두 COMP-3(packed decimal)다. Java에서 `double`/`float`로 옮기면 반올림 오차가 누적된다. **반드시 `BigDecimal` + 명시적 `RoundingMode`** 를 써야 한다.

---

## 6. 선택 모듈 3개

근거: 메모리 [batch-and-optional-modules], [ims-hierarchical-db], [auth-mq-db2-2pc], `README.md:62~98`, `app/app-*`.

### 6.0 통합 방식 — "베이스에 추가만" 한다

세 모듈은 **베이스 코드를 수정하지 않고 "추가"로만 통합**된다. 각 모듈은 자체 `cbl`/`cpy`/`csd`(+필요 시 `dcl`/`ddl`/`ims`)를 갖고, CSD에서 동일한 GROUP(CARDDEMO)에 새 PROGRAM/TRANSACTION을 추가 정의한다. 베이스와의 **유일한 접점은 메뉴 옵션 테이블에 항목을 추가**하는 것뿐이다 (메모리 [batch-and-optional-modules]).

| 모듈 디렉터리 | 기술 | 메뉴 노출 | 노출 옵션 |
|---|---|---|---|
| `app/app-transaction-type-db2` | DB2 | 관리자 메뉴 `COADM02Y` | 옵션 5/6 (COTRTLIC=CTLI, COTRTUPC=CTTU) |
| `app/app-authorization-ims-db2-mq` | IMS + DB2 + MQ | 사용자 메뉴 `COMEN02Y` | 옵션 11 (COPAUS0C=CPVS) |
| `app/app-vsam-mq` | MQ | 메뉴에 없음 | 트랜잭션 직접 기동 CDRD(CODATE01)/CDRA(COACCT01) |

### 6.1 app-transaction-type-db2 (DB2)

거래 유형 마스터를 **DB2 테이블**로 관리하는 모듈. `EXEC SQL`(DECLARE CURSOR FORWARD/BACKWARD, FETCH INTO `:host-var`, SELECT COUNT, UPDATE/DELETE, OPEN/CLOSE)을 사용한다. DCLGEN copybook(`dcl/DCLTRTYP.dcl`)이 테이블 ↔ COBOL 호스트변수 매핑을 담당하고, VARCHAR은 **49-level len+text 쌍**으로 표현된다. CSD에 `DB2ENTRY`/`DB2TRAN`(PLAN=CARDDEMO)을 정의한다.

> **Java 대응**: `EXEC SQL` 커서 = JDBC `ResultSet` 또는 JPA 쿼리. DCLGEN 호스트변수 = 엔티티/DTO. VARCHAR 49-level 쌍은 그냥 `String`이 된다.

### 6.2 app-authorization-ims-db2-mq (IMS + DB2 + MQ)

가장 복잡한 모듈이다. **보류 승인(pending authorization)** 업무를 **IMS 계층형 DB + DB2 + MQ** 협력으로 처리하며, **IMS와 DB2를 아우르는 2단계 커밋(2PC)** 을 시연한다 (`README.md:44` "Two-phase commit transactions across IMS DB and DB2", 메모리 [auth-mq-db2-2pc]).

#### 6.2.1 IMS 계층형 DB 구조 (HIDAM)

근거: 메모리 [ims-hierarchical-db], `app/app-authorization-ims-db2-mq/ims/*.dbd|*.psb`.

```
        DBPAUTX0 (HIDAM 1차 인덱스 DB)            DBPAUTP0 (메인 DB, HIDAM/VSAM)
        ACCESS=(INDEX,VSAM,PROT)                 ACCESS=(HIDAM,VSAM)
        ┌──────────────┐                         ┌─────────────────────────────────┐
        │ PAUTINDX (6) │── INDEX=ACCNTID ──────▶ │ [루트] PAUTSUM0 (보류승인 요약,100)│
        └──────────────┘   루트 키를 인덱싱        │   SEQ: ACCNTID (P=packed, 6byte) │
                                                 │            │ 1:N                  │
                                                 │            ▼                      │
                                                 │ [자식] PAUTDTL1 (보류승인 상세,200)│
                                                 │   SEQ: PAUT9CTS (C, 8byte)        │
                                                 └─────────────────────────────────┘
        HIDAM = "인덱스 DB + 데이터 DB" 한 쌍.
        인덱스로 루트를 키 직접 접근, 데이터 DB 안에서는 포인터로 트리 항해.
```

**계층형 ↔ Java**: 루트=부모 엔티티, 자식=소유된 컬렉션.
```java
class PendingAuthSummary {           // 루트 PAUTSUM0
    String acctId;                   // 키 ACCNTID
    List<PendingAuthDetail> details; // 자식 PAUTDTL1 (1:N)
}
```
RDB라면 PK/FK 2테이블 + JOIN이지만, **IMS는 물리 트리에 자식을 묻어두고 부모 경로로만 도달**한다. 자식을 독립 조회할 수 없고 **부모를 먼저 위치시켜야 한다** — 이것이 navigational DB의 본질이다.

**PSB/PCB** (= 프로그램이 보는 DB 뷰 + 권한):
- `PSBPAUTB.psb`: PCB명 `PAUTBPCB`, `PROCOPT=AP`(All=조회/갱신), `KEYLEN=14`. 온라인/업데이트용.
- `PSBPAUTL.psb`: PCB명 `PAUTLPCB`, `PROCOPT=L`(Load). 초기 적재용(로더 PAUDBLOD).
- PCB = JDBC Connection + 권한 + 커서 위치를 합친 개념. PROCOPT은 "이 프로그램이 뭘 할 수 있나"를 컴파일 시점에 고정(= Spring의 메서드 권한/readonly 트랜잭션과 유사하나 더 정적).

**두 접근 스타일** (둘 다 같은 IMS, 추상화 수준만 다름):
1. **`EXEC DLI` 호스트 명령**(고수준, 온라인). 예 `COPAUA0C.cbl:620` 근처:
   ```cobol
   EXEC DLI GU USING PCB(PAUT-PCB-NUM) SEGMENT(PAUTSUM0)
       INTO(PENDING-AUTH-SUMMARY) WHERE(ACCNTID = PA-ACCT-ID) END-EXEC
   ```
   - GU=Get Unique(키로 단건, =`SELECT...WHERE PK`), GNP=Get Next within Parent(자식 반복=커서 next), ISRT=Insert, REPL=Replace(반드시 직전 GET-HOLD로 읽은 세그먼트만), DLET=Delete.
   - 상태는 `DIBSTAT`를 검사. SPACES/'FW'=정상, 'GE'=세그먼트 없음(=row not found), 'II'=중복, 'GB'=DB끝.
2. **`CALL 'CBLTDLI'` + SSA**(저수준, 로더 `PAUDBLOD.CBL`). `EXEC DLI`를 전처리기가 이 콜로 풀어준다. SSA(Segment Search Argument)는 WHERE절을 수동 조립한 구조체다.

IMS gotcha: (a) 자식 도달엔 부모 위치가 선행, (b) REPL/DLET 전 GET-HOLD 필수(위치 기반 잠금), (c) 키 ACCNTID가 packed(COMP-3) → Java `Long`↔packed 변환 주의, (d) "없음"은 예외가 아니라 상태코드('GE')로 옴 → `Optional.empty()` 매핑.

#### 6.2.2 COPAUA0C — MQ 트리거 승인 처리기 (화면 없음)

근거: 메모리 [auth-mq-db2-2pc], `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`.

트랜잭션 CP00. **BMS 화면 없이 MQ 트리거로 기동되는 백그라운드 메시지 처리기**(Java로는 `@JmsListener`/MDB).
```
MAIN-PARA (:220)
  1000-INITIALIZE (:230)
     EXEC CICS RETRIEVE INTO(MQTM)   ← MQ 트리거가 넘긴 트리거 메시지 회수
     1100-OPEN-REQUEST-QUEUE (MQOPEN, :262)
  2000-MAIN-PROCESS (:323)  PERFORM UNTIL no-msg OR limit
     메시지 1건당:
        2100-EXTRACT-REQUEST-MSG  (UNSTRING으로 CSV 파싱)
        5000-PROCESS-AUTH
        ADD 1 처리카운트
        EXEC CICS SYNCPOINT (:335)   ← 메시지 단위 커밋 (IMS만 커밋)
        3100-READ-REQUEST-MQ (다음 MQGET)
  9000-TERMINATE (:940)  EXEC DLI TERM, MQCLOSE
```
- 5000-PROCESS-AUTH(:438): 1200-SCHEDULE-PSB(`EXEC DLI SCHD`) → XREF/ACCT/CUST READ(VSAM) → 5500 `EXEC DLI GU PAUTSUM0` → 6000-MAKE-DECISION → 7100 MQPUT 응답 → 8000-WRITE-AUTH-TO-DB.
- 6000-MAKE-DECISION(:657) 핵심 규칙: 가용신용 `WS-AVAILABLE-AMT = CREDIT-LIMIT - CREDIT-BALANCE`. 거래액 > 가용이면 DECLINE+INSUFFICIENT-FUND. resp '00'승인/'05'거절, reason 3100/4100/4200/4300/5100/5200/9000.
- 7100-SEND-RESPONSE(:738): `CALL 'MQPUT1'`(:758). 응답큐=요청메시지의 `MQMD-REPLYTOQ`, CORRELID=요청 MSGID 보존(표준 request/reply 상관).

**중요(비자명)**: COPAUA0C에서 **IMS 쓰기(ISRT/REPL)만 CICS UOW 안**에 있고, **MQ GET/PUT은 전부 `NO-SYNCPOINT`**(UOW 밖, 비트랜잭션)다 (`:389` MQGMO-NO-SYNCPOINT, `:753` MQPMO-NO-SYNCPOINT). 따라서 `:335`의 SYNCPOINT는 **IMS 단일 리소스만 커밋**하며, COPAUA0C 자체는 "MQ까지 묶은 2PC"가 아니다.

#### 6.2.3 진짜 2PC — 사기(fraud) 표시 흐름

근거: 메모리 [auth-mq-db2-2pc].

진짜 IMS+DB2 원자성은 **사기 표시** 기능에 있다. 오케스트레이터는 **COPAUS1C**(상세화면)이고, **COPAUS2C는 DB2 전용 워커**다.

- **COPAUS2C** (`:244`, 짧음): DB2 전용. `EXEC SQL INSERT INTO CARDDEMO.AUTHFRDS`(:142), `-803`(중복키)면 UPDATE(:223). **EXEC DLI 전혀 없음. SYNCPOINT/RETURN도 안 함** — `EXEC CICS RETURN`(:218)만. 즉 "DB2 변경만 하고 커밋은 호출자에 위임"하는 순수 워커. (호스트변수 `dcl/AUTHFRDS.dcl`, 테이블 `ddl/AUTHFRDS.ddl`, PK=CARD_NUM+AUTH_TS)
- **COPAUS1C** MARK-AUTH-FRAUD(`:230`, PF5 진입):
  ```
  READ-AUTH-RECORD (GHU로 세그 읽음)
     → fraud 플래그 토글
     → EXEC CICS LINK PROGRAM(COPAUS2C) COMMAREA(WS-FRAUD-DATA)  (:248)  ← DB2 INSERT 위임
     → 결과 검사 (:253)
         ├─ LINK 정상 & DB2 성공:
         │     UPDATE-AUTH-DETAILS (:520) → EXEC DLI REPL PAUTDTL1 (:525)  ← IMS에 fraud 표시
         │     → REPL 성공: TAKE-SYNCPOINT (:557 EXEC CICS SYNCPOINT)
         │                  = DB2 INSERT + IMS REPL 동시 커밋  ★진짜 2PC★
         └─ LINK 실패 OR DB2 실패 OR IMS REPL 실패:
               ROLL-BACK (:565 EXEC CICS SYNCPOINT ROLLBACK)
               = 둘 다 롤백
  ```

#### 6.2.4 2PC 흐름 다이어그램

```
  COPAUS1C (오케스트레이터)                      리소스 매니저
  ─────────────────────────                     ─────────────
  PF5 → MARK-AUTH-FRAUD
        │
        ├─ READ-AUTH-RECORD (GHU) ──────────────▶ [IMS] PAUTDTL1 읽기(HOLD)
        │
        ├─ EXEC CICS LINK COPAUS2C ────┐
        │                              ▼
        │                        COPAUS2C (DB2 워커)
        │                        EXEC SQL INSERT AUTHFRDS ──▶ [DB2] AUTHFRDS enlist
        │                        (커밋 안 함, RETURN만)
        │                              │
        │ ◀────────────────────────────┘ 결과 반환
        │
        ├─ EXEC DLI REPL PAUTDTL1 ──────────────▶ [IMS] fraud 플래그 갱신(enlist)
        │
        ▼
  ┌─────────────────────────────────────────────────────────────┐
  │   성공 경로:  EXEC CICS SYNCPOINT            (:557)            │
  │     → CICS가 코디네이터로서 IMS + DB2 양쪽에 prepare→commit    │
  │       (XA 2단계: 1단계 vote, 2단계 commit) → 원자적 커밋        │
  │                                                               │
  │   실패 경로:  EXEC CICS SYNCPOINT ROLLBACK   (:565)            │
  │     → IMS + DB2 양쪽 원자적 롤백                                │
  └─────────────────────────────────────────────────────────────┘
       ※ MQ는 이 2PC에 들어가지 않음 (COPAUA0C의 MQ는 NO-SYNCPOINT)
```

**모던 매핑**:

| CICS/메인프레임 | Java 대응 |
|---|---|
| CICS UOW (분산 트랜잭션) | JTA 분산 트랜잭션 |
| CICS (트랜잭션 코디네이터) | JTA `TransactionManager` (Atomikos/Narayana) |
| RM = IMS DB, DB2 | 각각 `XAResource` |
| `EXEC SQL` / `EXEC DLI` (자원 접근) | XAResource enlist |
| `EXEC CICS SYNCPOINT` | `UserTransaction.commit()` / `@Transactional` 정상 종료 |
| `EXEC CICS SYNCPOINT ROLLBACK` | `setRollbackOnly()` / 예외 throw |
| COPAUS1C가 LINK로 워커 호출 후 자기가 커밋 | "한 `@Transactional` 메서드에서 두 DAO 호출 후 메서드 끝에서 일괄 커밋" |

> **주의**: MQ는 위 2PC에 포함되지 않는다(COPAUA0C MQGET/PUT은 NO-SYNCPOINT). 운영에서 MQ-IMS XA를 원하면 `MQGMO-SYNCPOINT`로 바꿔야 하나, 데모는 단순화했다. 모던 전환 시 JMS+DB를 한 XA/JTA로 묶을지(메시지 유실 vs 중복)는 설계 결정이며, 현재는 at-least-once 처리 + 메시지 단위 IMS 커밋이므로 **멱등성 키(CARD_NUM+AUTH_TS) 활용을 권장**한다.

### 6.3 app-vsam-mq (MQ)

근거: 메모리 [batch-and-optional-modules], `app/app-vsam-mq`.

VSAM 데이터를 **MQ 비동기 request/reply** 패턴으로 노출하는 모듈. 메뉴에 없고 트랜잭션 CDRD(CODATE01, 시스템 날짜 조회)/CDRA(COACCT01, 계정 상세 조회)로 직접 기동된다 (`README.md:293~294`).
```
트리거 메시지 도착
   → EXEC CICS RETRIEVE 로 트리거 큐명 획득
   → CALL 'MQOPEN' / 'MQGET' (요청 수신)
   → 비즈니스 처리 (VSAM 조회)
   → CALL 'MQPUT' (응답 송신)
   → CALL 'MQCLOSE'
```
MQ 상수/구조는 IBM copybook(`CMQV`, `CMQODV`, `CMQGMOV` 등)을 COPY한다.

> **Java 대응**: 전형적 JMS request/reply. `@JmsListener`로 요청 큐 수신 → 처리 → `JmsTemplate.convertAndSend(replyTo, ...)`로 응답.

---

## 7. Java/Spring 마이그레이션 주의점

각 항목은 앞 장에서 다룬 내용을 **"마이그레이션 시 꼭 결정/조심할 것"** 관점으로 모은 체크리스트다.

### 7.1 고정소수점 — BigDecimal 필수 (★최우선)
- 금액·잔액·이자율은 거의 전부 `COMP-3`(packed decimal) 또는 `S9(p)V9(s)`다. **`double`/`float` 금지** → 반올림 오차 누적.
- 반드시 `BigDecimal` + 명시적 `RoundingMode`. COBOL `COMPUTE` 기본은 **truncate**이므로(예: 이자계산 CBACT04C `/1200`), 동일 결과를 원하면 `RoundingMode.DOWN`을, 정책상 반올림을 원하면 의도적으로 바꾸되 **차이를 문서화**할 것.
- `V`(implied decimal)는 저장된 점 문자가 아니라 scale 정의다 → `BigDecimal`의 scale로 대응.

### 7.2 데이터 형식 혼재
- 한 레코드 안에 DISPLAY(문자) / COMP(binary) / COMP-3(packed)가 섞여 있다. 바이트 단위 직렬화/역직렬화 시 각 필드의 저장형을 정확히 알아야 한다(5.4 표).
- 부호 처리: `S9` DISPLAY는 마지막 바이트 overpunch, COMP-3은 마지막 니블에 부호. 잘못 파싱하면 값이 깨진다.
- EBCDIC ↔ ASCII: 원본 데이터셋은 EBCDIC다. 파일을 그대로 가져오면 **코드페이지 변환**이 필요하다(특히 packed/binary 필드는 단순 텍스트 변환으로 깨지므로 필드별 처리).
- 고정길이 레코드: `PIC X(n)`은 공백 패딩된 고정폭이다. Java `String`으로 옮길 때 trim 정책을 정해야 한다.

### 7.3 날짜/타임스탬프 문자열
- 날짜는 대개 문자열(`YYYYMMDD` 등)로 다뤄진다. 예: CBACT04C는 `PARM='YYYYMMDD0'`로 처리일자를 받고(4.4), 타임스탬프를 DB2 형식 문자열로 조립한다.
- Java에서는 `LocalDate`/`LocalDateTime` + `DateTimeFormatter`로 옮기되, **경계(월말/윤년/타임존)** 처리를 명시. 원본의 문자열 포맷 가정을 깨지 않도록 입출력 경계에서만 포맷팅.

### 7.4 직접 대응이 없는 관용구 (그대로 옮기지 말 것)
| 관용구 | 문제 | 권장 처리 |
|---|---|---|
| **REDEFINES** | 같은 메모리를 다른 레이아웃으로 겹쳐 봄(union) | Java에 union 없음 → `ByteBuffer` 뷰 또는 별도 클래스 2개 + 변환 메서드 |
| **ALTER** (CBSTM03A) | GO TO 타깃을 런타임에 변경(자기수정 코드) | 추적 불가, 현대 린터 금지 → `switch` 상태머신/순차 호출로 평탄화 |
| **GO TO** (상태머신 회전) | 비구조적 제어흐름 | 명시적 루프/메서드 분해 |
| **OCCURS 하드코딩 상한** | 경계검사 없음, 오버런 위험(예: 51카드/10거래) | `List`로 풀되 **원본 데이터 가정 검증** |
| **제어블록 어드레싱**(PSA/TCB/TIOT) | z/OS 내부구조 의존 | Java 대응 없음 → 환경변수/설정으로 대체 |
| **88-level** | 값에 붙인 조건명 | boolean 술어 메서드 또는 `enum`(깔끔히 대응됨) |
| **packed key(ACCNTID)** | IMS 키가 COMP-3 | `Long`↔packed 변환 유틸 필요 |

### 7.5 pseudo-conversational → stateless
- CICS 트랜잭션 재기동 모델은 HTTP 무상태 요청과 동형이다(3.1). COMMAREA에 실어 나르던 상태를 **세션/토큰/요청 DTO**로 옮긴다.
- `XCTL` → redirect/forward, `LINK` → 서비스 호출로 분해.
- 브라우즈(STARTBR/READNEXT)는 **커서 기반 페이지네이션**(keyset pagination)으로 매핑하는 것이 자연스럽다.
- 데이터 주도 메뉴 라우팅(3.6)은 `Map<옵션, 핸들러>` 또는 라우팅 설정으로 옮긴다.

### 7.6 배치 → Spring Batch
- 야간 잡 스트림 = `Job`(여러 `Step` 연결). 각 CB* = `ItemReader`/`ItemProcessor`/`ItemWriter` 구성(4.3, 4.4).
- **control-break**(CBACT04C)은 그룹 집계로 모델링 — 정렬된 입력 가정에 의존하므로, RDB로 가면 `GROUP BY`/윈도우 함수로 재설계 가능.
- CLOSEFIL/OPENFIL 같은 "CICS-배치 자원 락 회피" 단계는 RDB 동시성으로 대체되어 대개 불필요.
- 명세서 생성(CBSTM03A/B)의 코드 기반 HTML은 **Thymeleaf/JSP 템플릿**으로 교체. ALTER/GO TO 디스패처는 반드시 재작성(7.4).

### 7.7 2PC → XA/JTA
- IMS+DB2 원자 커밋(6.2.3)은 JTA + `XAResource`로 매핑(6.2.4 표). `EXEC CICS SYNCPOINT` = `@Transactional` 메서드 정상 종료, `ROLLBACK` = 예외/`setRollbackOnly()`.
- MQ는 현재 2PC 밖(NO-SYNCPOINT)이다. 메시지를 트랜잭션에 묶을지(JMS XA) 여부는 **유실 vs 중복** 트레이드오프 설계 결정. 현재 at-least-once + 멱등성 키 전략을 유지/강화 권장.
- IMS 계층형 모델(6.2.1)을 그대로 RDB로 옮길지, aggregate(JPA `@OneToMany`)로 옮길지, 문서DB로 옮길지도 별도 판단 — navigational 접근을 단순 JOIN으로 바꾸면 "부모 선위치" 제약이 사라지므로 동작 차이를 검토.

### 7.8 보안
- **비밀번호 평문 저장/비교**(COSGN00C `:223`, USRSEC `CSUSR01Y`의 `SEC-USR-PWD`). 반드시 **해시+솔트**(BCrypt/Argon2)로 교체하고, 마이그레이션 시 일회성 재설정 흐름 필요.
- 권한 모델은 `SEC-USR-TYPE`('A'/'U') 2단계뿐 → Spring Security 롤/권한으로 확장 가능(현재 동작 보존하려면 최소 ADMIN/USER 2롤).
- 인증이 VSAM 파일 직접 조회(3.7)다 → 사용자 저장소를 DB/IdP로 이전 시 인증 경로 전체를 재설계.

---

## 8. 부록 — 파일 경로 색인 / 미검증 영역

> 경로는 모두 절대경로 기준 디렉터리 루트:
> `/Users/dongryunjeong/Documents/development/aws-mainframe-modernization-carddemo/`

### 8.1 디렉터리 구조

| 경로 | 내용 |
|---|---|
| `app/cbl/` | COBOL 프로그램(베이스). CO*(온라인 18) + CB*(배치 10) |
| `app/cpy/` | copybook(레코드 레이아웃 = 공유 DTO) |
| `app/cpy-bms/` | BMS 화면 symbolic map copybook(`*.CPY`) |
| `app/bms/` | BMS 화면 소스(`*.bms`) |
| `app/jcl/` | 배치 JCL 잡 |
| `app/csd/` | CICS 자원 정의(CSD) |
| `app/proc/`, `app/ctl/`, `app/maclib/`, `app/catlg/` | 프로시저/제어카드/매크로/카탈로그 |
| `app/data/` | 샘플 데이터(EBCDIC 등) |
| `app/asm/` | 어셈블러 소스 |
| `app/app-transaction-type-db2/` | 선택 모듈: DB2 거래유형 (`cbl/dcl/ddl/bms/csd/jcl`) |
| `app/app-authorization-ims-db2-mq/` | 선택 모듈: IMS+DB2+MQ 권한승인 (`cbl/cpy/dcl/ddl/ims/bms/csd/jcl/data`) |
| `app/app-vsam-mq/` | 선택 모듈: MQ (`cbl/csd`) |
| `scripts/` | 컴파일/실행 스크립트(`run_full_batch.sh`, `run_interest_calc.sh`, `local_compile.sh` 등) |
| `diagrams/` | 화면/흐름 다이어그램(PNG, `CARDDEMO-DataModel.drawio`) |
| `samples/` | 샘플 자료 |

### 8.2 핵심 파일 경로 색인

**온라인(3장)**
- 로그인: `app/cbl/COSGN00C.cbl` (분기 `:80`, USRSEC READ `:212`, 평문 PW 비교 `:223`, XCTL `:231`/`:236`)
- 메뉴: `app/cbl/COMEN01C.cbl`(사용자), `app/cbl/COADM01C.cbl`(관리자)
- 메뉴 테이블: `app/cpy/COMEN02Y.cpy`, `app/cpy/COADM02Y.cpy`
- 공용 commarea: `app/cpy/COCOM01Y.cpy` (= `CARDDEMO-COMMAREA`)

**배치(4장)**
- 거래입력: `app/cbl/CBTRN02C.cbl` / JCL `app/jcl/POSTTRAN.jcl`
- 이자계산: `app/cbl/CBACT04C.cbl` / JCL `app/jcl/INTCALC.jcl`(STEP15)
- 명세서: `app/cbl/CBSTM03A.CBL`(드라이버, ALTER `:296~`, 제어블록 `:240~`) + `app/cbl/CBSTM03B.CBL`(I/O 서브루틴) / JCL `app/jcl/CREASTMT.JCL`
- 야간 시퀀스: `scripts/run_full_batch.sh`, 자원 락: `app/jcl/CLOSEFIL.jcl`/`app/jcl/OPENFIL.jcl`

**데이터(5장)**
- 레코드 copybook: `app/cpy/CVACT01Y.cpy`(계정), `CVACT02Y`(카드), `CVCUS01Y`(고객), `CVACT03Y`(교차참조), `CVTRA05Y`(거래), `CVTRA06Y`(일일거래), `CVTRA01Y`(카테고리잔액), `CSUSR01Y`(사용자보안)

**선택 모듈(6장)**
- DB2: `app/app-transaction-type-db2/cbl/`, DCLGEN `app/app-transaction-type-db2/dcl/DCLTRTYP.dcl`
- IMS DBD/PSB: `app/app-authorization-ims-db2-mq/ims/DBPAUTP0.dbd`, `DBPAUTX0.dbd`, `PSBPAUTB.psb`, `PSBPAUTL.psb`
- 승인 처리기: `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl` (SYNCPOINT `:335`, GU `:620~`, MQPUT1 `:758`)
- 2PC: `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl`(오케스트레이터, LINK `:248`, REPL `:525`, SYNCPOINT `:557`, ROLLBACK `:565`) + `COPAUS2C.cbl`(DB2 워커, INSERT `:142`, RETURN `:218`)
- DB2 사기테이블: `app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl`, `ddl/AUTHFRDS.ddl`
- IMS 로더: `app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL`(`CBLTDLI`+SSA, SSA `:113~`)
- MQ: `app/app-vsam-mq/cbl/`

### 8.3 미검증 / 추측으로 남은 영역

아래는 본 문서 작성 시점에 **소스로 완전히 검증하지 못했거나 추정**한 부분이다. 마이그레이션 전 확정 필요.

1. **온라인 프로그램 개별 동작 세부** (COACT*/COCRD*/COTRN*/COBIL00C/CORPT00C/COUSR*/COBSWAIT): 3.4 표의 "기능"은 네이밍·메뉴 기준 **추정**이며, COSGN00C 외 각 프로그램의 흐름은 개별 정독 전이다.
2. **CBACT01C/02C/03C, CBTRN01C/03C, CBCUS01C**: 마스터/거래 처리 배치이나 상세 흐름 미정독. CBTRN02C·CBACT04C·CBSTM03A/B만 정독 완료.
3. **CBEXPORT / CBIMPORT 및 EBCDIC 데이터 추출/적재**: 최근 커밋(59cc6c2)에서 추가됨. copybook `CVEXPORT.cpy`, JCL `CBEXPORT.jcl`/`CBIMPORT.jcl` 존재만 확인, 로직 미분석.
4. **IMS 기타 파일**: `PADFLDBD`/`PASFLDBD.DBD`, `DLIGSAMP.PSB` 등은 같은 디렉터리에 있으나 미정독.
5. **COPAUS2C의 EXEC SQL 외 세부 / COPAUS0C(=CPVS 진입점)**: 메뉴 노출 옵션 11의 진입 프로그램 COPAUS0C 자체 흐름 미정독(2PC 핵심은 COPAUS1C/2C로 확인됨).
6. **스케줄러 연동**: `app/scheduler/`(CA7/Control-M 파일, 최근 커밋)로 야간 잡이 운영 스케줄링되나, 정확한 잡 의존성 그래프는 스케줄러 파일 정독 전. `scripts/run_full_batch.sh`의 순서를 1차 근거로 사용했다.
7. **라인 번호**: 본문에 단 `:라인`은 분석 시점 기준이며, 이후 소스 편집으로 어긋날 수 있다(특히 COPAUA0C/COPAUS1C의 세부 라인). 액션 전 grep으로 재확인 권장.

---

*문서 끝. 근거 출처: cobol-explainer 에이전트 메모리 6건([copybook-record-layouts], [online-pseudoconv-pattern], [batch-and-optional-modules], [ims-hierarchical-db], [statement-generation-cbstm03], [auth-mq-db2-2pc]) + README.md / 디렉터리 구조 / COSGN00C 핵심 라인 재확인.*
