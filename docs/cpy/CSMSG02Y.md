# CSMSG02Y — ABEND 루틴 작업 영역 (CABENDD)

- **유형**: Copybook
- **한 줄 요약**: CICS 프로그램이 비정상 종료(ABEND) 직전에 오류 정보를 수집해 화면에 전송하기 위한 공통 작업 영역 레코드를 정의한다.

---

## 기능 설명

`CSMSG02Y.cpy`는 파일 헤더 주석에 `CABENDD.CPY`라는 원래 이름이 명시된 공용 오류 처리 copybook이다(라인 000800). 이 copybook은 단 하나의 01레벨 그룹 `ABEND-DATA`를 정의하며, ABEND(비정상 종료) 발생 시 원인·위치·메시지 텍스트를 담아 CICS 터미널 화면에 전송(`EXEC CICS SEND`)하고 이어서 `EXEC CICS ABEND ABCODE('9999')`로 프로그램을 강제 종료하는 공통 `ABEND-ROUTINE` 단락에서 사용된다.

패턴 요약:
1. 오류 감지 시점에 `ABEND-CODE`, `ABEND-CULPRIT`, `ABEND-REASON`, `ABEND-MSG`를 채운다.
2. `PERFORM ABEND-ROUTINE`을 호출한다.
3. `ABEND-ROUTINE`은 `ABEND-MSG`가 비어 있으면 기본 메시지를 채우고, `EXEC CICS SEND FROM(ABEND-DATA)` 로 134바이트 전체를 단순 텍스트 스트림으로 터미널에 출력한 뒤, `EXEC CICS HANDLE ABEND CANCEL`로 재귀 ABEND 훅을 해제하고 `EXEC CICS ABEND ABCODE('9999')`로 트랜잭션을 종료한다 (COCRDUPC.cbl 라인 1531–1552 참조).

이 구조는 체계적인 예외 계층 없이 모든 오류를 단일 공통 단락으로 라우팅하는 COBOL의 전통적 오류 처리 관용구이다.

---

## 필드 레이아웃

전체 레코드 길이: **134바이트** (DISPLAY 기준)

| 필드명 | 레벨 | PIC / USAGE | 바이트 | 초기값 | 의미 |
|---|---|---|---|---|---|
| `ABEND-DATA` | 01 | GROUP | 134 | — | ABEND 정보 전체 컨테이너 |
| `ABEND-CODE` | 05 | `PIC X(4)` | 4 | SPACES | 오류 식별 코드 (예: '0001'); 프로그램별로 임의 정의 |
| `ABEND-CULPRIT` | 05 | `PIC X(8)` | 8 | SPACES | 오류를 일으킨 프로그램명 (PIC X(8) = 표준 IBM 프로그램명 길이) |
| `ABEND-REASON` | 05 | `PIC X(50)` | 50 | SPACES | 오류 원인 설명 텍스트 (자유 형식, 주로 파일명·VSAM 상태 코드 등 맥락 정보) |
| `ABEND-MSG` | 05 | `PIC X(72)` | 72 | SPACES | 터미널 표시용 최종 메시지 텍스트; LOW-VALUES이면 런타임에 기본값 'UNEXPECTED ABEND OCCURRED.' 삽입 |

> **참고**: 88레벨 조건명, REDEFINES, OCCURS 없음. 모든 필드 DISPLAY(기본) 문자 타입, VALUE SPACES로 초기화.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — 이 copybook은 데이터 정의만 포함하며 PROCEDURE DIVISION 코드가 없다.
- **데이터셋/파일/DB 테이블**: 없음 — 순수 메모리 작업 영역이며, 외부 파일·DB와 직접 연결되지 않는다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음

**이 copybook을 COPY하는 프로그램 목록** (grep 결과 기준):

| 프로그램 | 경로 | 비고 |
|---|---|---|
| `COCRDUPC` | `app/cbl/COCRDUPC.cbl` (라인 343) | 카드 정보 업데이트 (온라인 CICS) |
| `COACTUPC` | `app/cbl/COACTUPC.cbl` (라인 632) | 계정 업데이트 (온라인 CICS) |
| `COACTVWC` | `app/cbl/COACTVWC.cbl` (라인 238) | 계정 조회 (온라인 CICS) |
| `COCRDSLC` | `app/cbl/COCRDSLC.cbl` (라인 224) | 카드 목록 조회 (온라인 CICS) |
| `COCRDLIC` | `app/cbl/COCRDLIC.cbl` (라인 283) | 카드 목록 (주석 처리됨 — `*COPY CSMSG02Y`) |
| `COTRTUPC` | `app/app-transaction-type-db2/cbl/COTRTUPC.cbl` (라인 274) | 거래 유형 업데이트 (DB2 옵션 모듈) |
| `COPAUS1C` | `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl` (라인 135) | 사기 표시 오케스트레이터 (IMS/DB2/MQ) |
| `COPAUS0C` | `app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl` (라인 141) | 승인 처리 (IMS/DB2/MQ) |

> `COCRDLIC.cbl` 라인 283은 `*COPY CSMSG02Y.`로 주석 처리되어 있다. 해당 프로그램은 `ABEND-DATA` 그룹을 인라인으로 재선언하고 있을 가능성이 있다(추측).

---

## Java / 현대화 노트

### 1. Java 매핑

```java
/**
 * CSMSG02Y (CABENDD.CPY) 대응 Java 클래스.
 * COBOL ABEND-DATA 01레벨 그룹 → 오류 정보 VO.
 */
public class AbendData {
    private String abendCode;     // PIC X(4)  — 4자 오류 코드 ("0001" 등)
    private String abendCulprit;  // PIC X(8)  — 8자 프로그램명
    private String abendReason;   // PIC X(50) — 50자 원인 설명
    private String abendMsg;      // PIC X(72) — 72자 최종 메시지

    // 기본 메시지 폴백 (ABEND-ROUTINE 로직 반영)
    public String getEffectiveMessage() {
        return (abendMsg == null || abendMsg.isBlank())
            ? "UNEXPECTED ABEND OCCURRED."
            : abendMsg;
    }
}
```

### 2. ABEND-ROUTINE 패턴의 Java 대응

COBOL의 `ABEND-ROUTINE` 단락은 Java 예외 처리와 개념적으로 대응하지만 몇 가지 차이가 있다.

| COBOL 패턴 | Java 대응 |
|---|---|
| `ABEND-CODE = '0001'` | 애플리케이션 고유 오류 코드 (`enum ErrorCode`) |
| `ABEND-CULPRIT = LIT-THISPGM` | `Thread.currentThread().getStackTrace()` 또는 로거의 클래스명 |
| `ABEND-REASON` | 예외 메시지 / cause chain |
| `ABEND-MSG` | 최종 사용자 표시 메시지 |
| `EXEC CICS SEND FROM(ABEND-DATA)` | HTTP 에러 응답 또는 UI 에러 다이얼로그 |
| `EXEC CICS ABEND ABCODE('9999')` | `throw new RuntimeException(...)` 또는 `System.exit(1)` |

```java
// ABEND-ROUTINE 동등 Java 패턴
private void abendRoutine(AbendData data) {
    String msg = data.getEffectiveMessage();
    data.setAbendCulprit(this.getClass().getSimpleName());
    // 화면 전송 → REST 응답 또는 로그
    log.error("ABEND [{}] in {}: {} — {}", data.getAbendCode(),
              data.getAbendCulprit(), data.getAbendReason(), msg);
    throw new AbendException(data.getAbendCode(), msg);
}
```

### 3. 고정 길이 필드 주의사항

- `PIC X(4)`, `PIC X(8)`, `PIC X(50)`, `PIC X(72)` 모두 DISPLAY(EBCDIC) 고정 길이 문자열이다. COBOL에서는 초과 길이 값을 잘라내고 미달 시 오른쪽을 공백으로 채운다. Java 마이그레이션 시 `String.format("%-8s", culprit).substring(0, 8)` 형태의 패딩·트런케이션 처리를 명시적으로 구현해야 한다.
- `EXEC CICS SEND FROM(ABEND-DATA) LENGTH(LENGTH OF ABEND-DATA)` 는 134바이트 원시 바이트 스트림을 터미널에 그대로 보내므로, 마이그레이션 후에는 구조화된 오류 응답(JSON/HTTP)으로 대체해야 한다.

### 4. 공통 오류 처리 인프라로 격상 권장

현재 구조는 각 프로그램이 `CSMSG02Y`를 COPY해 독립 `ABEND-DATA` 인스턴스를 가진다. Java에서는 공통 `AbendData` 클래스 + AOP 기반 예외 인터셉터 또는 Spring의 `@ControllerAdvice`를 사용해 오류 처리를 중앙화하는 것이 권장된다.

### 5. COCRDLIC 주석 처리 주의

`COCRDLIC.cbl` 라인 283의 `*COPY CSMSG02Y.`는 주석 처리되어 있다. 해당 프로그램이 `ABEND-DATA`를 어떻게 선언하는지 별도 확인이 필요하다.
