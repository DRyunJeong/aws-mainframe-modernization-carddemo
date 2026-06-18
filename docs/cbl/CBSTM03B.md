# CBSTM03B — 명세서 생성용 범용 파일 I/O 서브루틴

- **유형**: 배치 COBOL (서브루틴)
- **한 줄 요약**: 명세서 생성 드라이버 CBSTM03A가 `CALL`로 호출하는 범용 파일 핸들러로, TRNXFILE/XREFFILE/CUSTFILE/ACCTFILE 4개 VSAM 파일의 OPEN/READ/CLOSE를 하나의 인터페이스(`LK-M03B-AREA`)로 대행한다.

## 기능 설명

이 프로그램은 독자적인 비즈니스 로직이 없는 순수 I/O 대행 서브루틴이다. 헤더 주석(5~9행, 25~27행)에 "명세서 생성 프로그램이 호출하며 파일 핸들링을 한다"고 명시되어 있다.

호출 측(CBSTM03A)이 공유 통신 영역 `LK-M03B-AREA`에 (1) 처리할 논리 파일명(DD명), (2) 오퍼레이션 코드(O/C/R/K), (3) 키 정보를 채워 `CALL 'CBSTM03B' USING ...`로 호출하면, 이 프로그램은:

1. `EVALUATE LK-M03B-DD`(118~128행)로 4개 파일 중 어느 것을 처리할지 분기하고,
2. 각 파일별 단락에서 `IF M03B-OPEN / M03B-READ / M03B-READ-K / M03B-CLOSE`(예: 135~149행)로 오퍼레이션을 분기 실행하며,
3. 결과인 2바이트 파일 상태(FILE STATUS)를 `LK-M03B-RC`에 담아(예: 152행 `MOVE TRNXFILE-STATUS TO LK-M03B-RC`),
4. 읽은 레코드를 범용 버퍼 `LK-M03B-FLDT`(PIC X(1000))에 `READ ... INTO`(141행 등)로 실어 호출 측에 반환한다.

호출 측은 반환된 `LK-M03B-FLDT`(1000바이트 통짜 버퍼)를 자신이 가진 해당 copybook 레코드 구조로 다시 해석한다. 즉 이 프로그램은 레코드의 "의미"를 전혀 모르고, 바이트 덩어리를 읽어 넘기기만 하는 얇은 데이터 접근 계층이다.

Java로 보면 단일 DAO 파사드 메서드 `byte[] io(String file, char op, String key)`에 해당하고, 의미 해석(역직렬화)은 호출 측 책임이다.

## 입력 / 출력

- **입력**:
  - `LK-M03B-AREA`(LINKAGE, 100~112행) — 호출 측이 채우는 요청 파라미터: DD명, 오퍼레이션 코드, 키, 키 길이.
  - VSAM(KSDS) 파일 4종을 OPEN INPUT으로 읽음 — `TRNX-FILE`, `XREF-FILE`, `CUST-FILE`, `ACCT-FILE`(31~53행).
- **출력**:
  - `LK-M03B-RC`(2바이트 파일 상태) — 호출 측으로 반환되는 결과 코드(`'00'`/`'04'`=정상, `'10'`=EOF가 일반적인 관례. (추측) — 이 프로그램 자체는 코드값을 해석하지 않고 그대로 전달만 함).
  - `LK-M03B-FLDT`(1000바이트) — READ로 읽어들인 레코드 원본 바이트.
  - 이 프로그램은 어떤 디스크 파일에도 WRITE하지 않는다(아래 핵심 로직 참고).

## 의존성

- **COPY (카피북)**: 없음. 이 소스에는 단 한 줄의 `COPY` 문도 없다. FD 레코드 레이아웃(59~78행)과 통신 영역(100~112행)이 모두 인라인으로 직접 정의되어 있다. 단, FD 정의는 키 필드 + 나머지(`...-DATA`)만 구분한 최소 형태라, 실제 필드 해석은 호출 측 CBSTM03A가 보유한 copybook(CVTRA05Y/CVACT03Y/CVCUS01Y/CVACT01Y 등)에 의존한다 — 즉 카피북 의존성은 이 프로그램이 아니라 호출 측에 있음(메모리 `[[copybook-record-layouts]]` 참고).
- **호출 프로그램 (CALL/XCTL/LINK)**: 이 프로그램은 다른 프로그램을 호출하지 않는다(`CALL`/`XCTL`/`LINK` 없음). 자신이 **호출당하는** 쪽 — 호출자는 CBSTM03A.CBL(메모리 `[[statement-generation-cbstm03]]`).
- **데이터셋/파일/DB 테이블**:
  - `TRNXFILE` → `TRNX-FILE`, INDEXED(KSDS), ACCESS SEQUENTIAL, RECORD KEY `FD-TRNXS-ID`(카드번호 16 + 거래ID 16 = 32바이트), 레코드 350바이트(31~35, 58~63행).
  - `XREFFILE` → `XREF-FILE`, INDEXED, ACCESS SEQUENTIAL, RECORD KEY `FD-XREF-CARD-NUM`(16바이트), 레코드 50바이트(37~41, 65~68행).
  - `CUSTFILE` → `CUST-FILE`, INDEXED, ACCESS **RANDOM**, RECORD KEY `FD-CUST-ID`(9바이트), 레코드 500바이트(43~47, 70~73행).
  - `ACCTFILE` → `ACCT-FILE`, INDEXED, ACCESS **RANDOM**, RECORD KEY `FD-ACCT-ID`(PIC 9(11) 숫자키), 레코드 300바이트(49~53, 75~78행).
  - DB2/SQL 테이블 사용 없음.
- **트랜잭션 ID 또는 EXEC PGM**: 해당 없음(서브루틴이므로 자체 트랜잭션 ID/JCL EXEC PGM 없음). 실행 단위로는 호출자 CBSTM03A가 job `CREASTMT`(app/jcl/CREASTMT.JCL)로 구동될 때 그 안에서 동적으로 `CALL`된다.

## 핵심 로직 흐름

1. **진입 / 디스패치 (0000-START, 116~128행)**: `PROCEDURE DIVISION USING LK-M03B-AREA`로 통신 영역 한 덩어리를 받는다. `EVALUATE LK-M03B-DD`로 DD명에 따라 4개 처리 단락 중 하나를 `PERFORM ... THRU ...-EXIT`로 호출. 알 수 없는 DD명이면 `WHEN OTHER GO TO 9999-GOBACK`(127~128행)으로 아무것도 안 하고 종료.

2. **파일별 처리 단락 (네 단락이 동일 패턴)**:
   - `1000-TRNXFILE-PROC`(133행~): `M03B-OPEN`→`OPEN INPUT`, `M03B-READ`→`READ ... INTO LK-M03B-FLDT`(순차 읽기), `M03B-CLOSE`→`CLOSE`.
   - `2000-XREFFILE-PROC`(157행~): TRNXFILE과 동일 — 순차 READ.
   - `3000-CUSTFILE-PROC`(181행~): OPEN/CLOSE는 동일하되 읽기는 `M03B-READ-K`(키 읽기). `MOVE LK-M03B-KEY (1:LK-M03B-KEY-LN) TO FD-CUST-ID` 후 `READ`(188~193행) — 호출 측이 넘긴 가변 길이 키를 **부분참조**로 잘라 레코드 키에 세팅하고 랜덤 READ.
   - `4000-ACCTFILE-PROC`(206행~): CUSTFILE과 동일 패턴, 키 읽기(213~218행).

3. **결과 반환 (각 단락의 `...900-EXIT`)**: OPEN/READ/CLOSE 어느 경로든 `GO TO ...900-EXIT`로 모여, 그 단락에서 해당 파일 상태를 `LK-M03B-RC`로 MOVE(152/176/201/226행). 즉 모든 오퍼레이션은 마지막에 반드시 파일 상태 코드를 채워 돌려준다.

4. **종료 (9999-GOBACK, 130~131행)**: `GOBACK`으로 호출 측에 복귀.

**비자명 포인트 / 주의**:
- **선언만 되고 구현 안 된 오퍼레이션**: 88레벨에 `M03B-WRITE`('W', 107행)와 `M03B-REWRITE`('Z', 108행)가 정의돼 있으나, **어떤 단락에도 이를 처리하는 `IF`가 없다**. 또한 모든 파일을 `OPEN INPUT`(읽기 전용)으로만 연다(136/160/184/209행). 따라서 'W'/'Z' 코드로 호출하면 어떤 OPEN/READ/CLOSE 분기에도 걸리지 않고 그냥 `...900-EXIT`로 떨어져 직전 파일 상태만 반환된다 — 사실상 무동작(no-op). 향후 확장을 위한 자리표시자로 보임. (추측)
- **`READ ... INTO`의 의미**: 레코드를 FD 영역으로 읽은 뒤 `LK-M03B-FLDT`로 복사한다. FD 레코드 길이(예: TRNXFILE 350바이트)가 버퍼 1000바이트보다 짧으므로 나머지는 공백 채움 — 호출 측은 자기 copybook 길이만큼만 잘라 쓰면 됨.
- **단락 폴스루(fall-through) 회피**: 각 처리 단락은 오퍼레이션 실행 직후 즉시 `GO TO ...900-EXIT`로 빠져 다음 `IF`로 흘러내리지 않게 막아두었다. 한 호출에 한 오퍼레이션만 수행되는 구조.
- **`LK-M03B-KEY-LN`은 PIC S9(4)**(111행, DISPLAY 부호숫자) — 부분참조 `(1:LK-M03B-KEY-LN)`의 길이로 쓰이므로 호출 측이 올바른 키 길이를 넣어줘야 한다(0이나 음수면 런타임 오류 위험). 경계 검사 없음.

## Java/현대화 노트

- **전체 구조 = DAO 파사드**: 이 서브루틴은 "파일명 + 오퍼레이션 코드 + 키"를 받아 바이트 레코드를 돌려주는 단일 진입점이다. Java에서는 `interface RecordDao { byte[] read(String file, String key); void open(String file); void close(String file); }` 또는 파일별 Repository로 자연스럽게 분해된다. `EVALUATE LK-M03B-DD`(118행)는 `switch`(또는 `Map<String, Repository>` 전략 패턴)로 대체.
- **통신 영역(`LK-M03B-AREA`) → 요청/응답 객체**: 입력 파라미터(DD명·op·key)와 출력(rc·레코드)이 한 구조에 섞여 있다. Java에서는 입력 DTO와 결과(예외 또는 `Optional<byte[]>`)를 분리하는 게 깔끔하다. `LK-M03B-RC`(파일 상태 2바이트)는 예외 또는 enum 결과 코드로: `'00'`/`'04'`→정상, `'10'`→EOF(예: `Optional.empty()` 또는 커스텀 EOF 신호), 그 외→`IOException`.
- **`LK-M03B-FLDT`(PIC X(1000)) → `byte[]` 또는 미파싱 `String`**: 의도적으로 타입 없는 통짜 버퍼다. Java로 옮길 때 핵심 선택지는 두 가지: (a) 이 무지성 바이트 전달을 유지하고 호출 측이 파싱, (b) 각 Repository가 `read()` 단계에서 바로 도메인 객체(`TransactionRecord`/`CustomerRecord` 등)로 역직렬화. 현대화 관점에서는 (b)가 타입 안전하므로 권장 — 단 EBCDIC↔ASCII, 고정폭, COMP-3 필드 변환은 파싱 시 반드시 처리.
- **`ACCESS RANDOM` vs `SEQUENTIAL` 차이 보존**: CUSTFILE/ACCTFILE은 키 기반 단건 조회(랜덤), TRNXFILE/XREFFILE은 전체 순회(순차)다. 이는 호출 측의 사용 패턴(XREF/거래는 풀스캔, 고객/계정은 PK 조회)을 반영하므로, Java로 옮길 때 `findById()`(랜덤) vs `findAll()`/`Iterator`(순차)로 구분해 설계하면 의도가 보존된다.
- **부분참조 키(`(1:KEY-LN)`)**: 가변 길이 키를 다루는 COBOL 관용구. Java에서는 그냥 `String key`(혹은 `key.substring(0, len)`)이며 길이 파라미터가 따로 필요 없다. 옮길 때 `KEY-LN` 파라미터는 제거 가능.
- **숫자 키 주의**: `FD-ACCT-ID PIC 9(11)`(77행)는 숫자형 레코드 키지만 다른 키들은 문자형이다. `LK-M03B-KEY`(X(25))의 부분참조를 숫자 필드에 MOVE하므로 자릿수/부호/공백 처리에 주의 — Java에서는 11자리 계정번호를 `long` 또는 자릿수 보존이 필요하면 11자리 0-패딩 `String`으로.
- **선언만 된 WRITE/REWRITE는 옮기지 말 것**: 미구현 'W'/'Z' 분기는 죽은 인터페이스다. 현대화 시 실제 쓰기 요구가 있으면 명시적으로 구현하고, 없으면 인터페이스에서 제거해 혼란을 없앤다.
- **`GO TO ...-EXIT` 관용구**: 단락 내 조기 탈출용 `GO TO`는 Java의 `return`(메서드로 분해 시) 또는 `break`/조건 분기로 평탄화된다. 이 프로그램의 GO TO는 모두 같은 단락 내 EXIT로 가는 단순 패턴이라(드라이버 CBSTM03A의 `ALTER` 자기수정 코드와 달리) 변환이 어렵지 않다.

관련 메모리: `[[statement-generation-cbstm03]]`(호출자 CBSTM03A·전체 명세서 배치 흐름), `[[copybook-record-layouts]]`(FLDT를 해석하는 실제 레코드 레이아웃).
