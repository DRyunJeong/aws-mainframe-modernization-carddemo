# WAITSTEP — 지정 시간 대기 유틸리티 잡

- **유형**: JCL
- **한 줄 요약**: COBSWAIT 프로그램에 센티초(1/100초) 값을 SYSIN으로 전달하여 배치 스텝 사이에 인위적 지연을 삽입하는 단일 스텝 유틸리티 잡.

---

## 기능 설명

WAITSTEP은 배치 흐름 제어를 위한 최소 JCL 잡이다.
JCL 내부에 `SYSIN DD *` 인라인 데이터로 8자리 센티초 값(`00003600` = 36초)을 포함하고,
이를 `EXEC PGM=COBSWAIT`로 기동되는 COBOL 프로그램에 전달한다.

COBSWAIT는 해당 값을 이진 정수(`PIC 9(8) COMP`)로 변환한 뒤 어셈블러 서브루틴 `MVSWAIT`를 호출한다.
`MVSWAIT`(app/asm/MVSWAIT.asm)는 z/OS `ASMWAIT` 매크로(내부적으로 `STIMER WAIT,BINTVL=` SVC에 해당)를 호출하여
태스크를 지정 인터벌 동안 블로킹 대기시킨 뒤 레지스터를 복원하고 복귀한다.

전형적인 사용 시나리오:

- 선행 스텝이 VSAM 파일의 enqueue(ENQ)를 해제하기 전에 후행 스텝이 너무 빨리 기동되는 경우 완충 딜레이 삽입.
- Control-M / CA7 스케줄러 JCL에서 외부 자원(파일 오픈, DB2 테이블 준비 등) 대기.
- CICS CLOSEFIL → OPENFIL 사이처럼 VSAM 파일이 배타적으로 닫혀 있어야 하는 구간에 후속 잡이 진입하지 못하도록 지연.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|---------------|------|
| WAIT   | `PGM=COBSWAIT` | SYSIN에서 8바이트 센티초 값을 읽어 `MVSWAIT` 어셈블러 루틴을 통해 OS 타이머 대기 수행 (라인 22) |

스텝은 단 하나이며 조건부 분기(`COND=`) 없이 무조건 실행된다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음. 카탈로그 프로시저나 INCLUDE를 사용하지 않는다.

- **호출 프로그램 (EXEC PGM)**:
  - `COBSWAIT` (라인 22) — `STEPLIB`의 `AWS.M2.CARDDEMO.LOADLIB`에서 로드되는 COBOL 배치 유틸리티 (app/cbl/COBSWAIT.cbl).
  - `MVSWAIT` — COBSWAIT가 내부적으로 `CALL 'MVSWAIT'`로 호출하는 z/OS 어셈블러 서브루틴 (app/asm/MVSWAIT.asm). `ASMWAIT` 매크로로 OS 인터벌 타이머를 기동하고, 완료 후 레지스터 14-12를 복원하고 R15=0(정상)을 설정한 뒤 복귀한다. `STEPLIB`의 동일 LOADLIB에 링크 편집되어 있어야 한다.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.LOADLIB` (라인 23, DISP=SHR) — COBSWAIT 및 MVSWAIT 로드 모듈 라이브러리.
  - `SYSOUT=*` (라인 24) — 시스템 메시지 출력 (실질적으로 아무것도 기록되지 않음).
  - 인라인 SYSIN 데이터 `00003600` (라인 26) — 대기 시간 지정값(36초). 이 값을 수정하면 대기 시간을 변경할 수 있다. 주석 `VALUE IN CENTISECONDS`는 JCL 인라인 데이터의 일부이나 COBSWAIT의 `ACCEPT`는 컬럼 1-8만 읽으므로 뒤의 주석 텍스트는 무시된다.

- **선행/후행 잡**: 명시적 선행/후행 잡은 JCL에 기재되지 않았다. 실제 배치 스케줄(Control-M / CA7)에서 이 잡은 CLOSEFIL 계열 잡 이후, OPENFIL 계열 잡 이전의 완충 구간에 삽입되는 형태로 운용될 것으로 예상된다(추측 — 스케줄러 정의 별도 확인 필요).

---

## Java/현대화 노트

### COBSWAIT + MVSWAIT 연계 구조의 Java 대응

WAITSTEP 잡은 JCL → COBOL(COBSWAIT) → Assembler(MVSWAIT) 3계층 구조로 하나의 단순한 "슬립"을 구현한다.
Java로 마이그레이션하면 이 전체가 `Thread.sleep()` 한 줄로 대체된다.

```java
/**
 * WAITSTEP JCL + COBSWAIT + MVSWAIT 전체에 대응하는 Java 구현.
 * SYSIN DD * 의 첫 번째 토큰(8자리 센티초)을 stdin으로 읽어 슬립.
 */
public class WaitStep {
    public static void main(String[] args) throws Exception {
        // ACCEPT PARM-VALUE FROM SYSIN (COBSWAIT.cbl 라인 36)
        try (java.util.Scanner sc = new java.util.Scanner(System.in)) {
            String raw = sc.next();                         // "00003600"

            // MOVE PARM-VALUE TO MVSWAIT-TIME (PIC 9(8) COMP)
            int centiseconds = Integer.parseInt(raw.trim()); // 3600

            // CALL 'MVSWAIT' → ASMWAIT → Thread.sleep
            Thread.sleep(centiseconds * 10L);               // 센티초 × 10 = 밀리초
        }
        // STOP RUN → RC=0
    }
}
```

### 핵심 변환 주의사항

| COBOL/JCL 요소 | Java 대응 | 주의 |
|---|---|---|
| `SYSIN DD *` 인라인 데이터 | `System.in` (stdin 리다이렉션) | 컨테이너 환경에서는 환경변수나 args 방식으로 전환 권장 |
| `PIC X(8)` → `PIC 9(8) COMP` MOVE | `Integer.parseInt(String)` | 비숫자 문자 포함 시 COBOL은 S0C7 ABEND, Java는 `NumberFormatException` — 명시적 예외 처리 필수 |
| `MVSWAIT` + `ASMWAIT` 매크로 | `Thread.sleep(millis)` | ASMWAIT/STIMER는 OS 비선점 인터벌 타이머; Java sleep은 인터럽트(`InterruptedException`) 가능 — 배치에서는 보통 무시해도 되나 명시적 처리 권장 |
| 센티초(1/100초) 단위 | 밀리초(1/1000초) 단위 | `centiseconds × 10L` 변환 누락 시 10배 오차 발생 |
| `STEPLIB DD DSN=AWS.M2.CARDDEMO.LOADLIB` | 의존 JAR/클래스패스 | 마이그레이션 후 별도 라이브러리 불필요 |

### MVSWAIT 어셈블러 루틴 분석 (app/asm/MVSWAIT.asm)

MVSWAIT의 실제 동작(소스 확인 완료):

1. `STM 14,12,12(13)` — 호출자(COBSWAIT) 레지스터 14-12를 세이브 에리어에 저장.
2. `L 5,0(1)` — R1(파라미터 리스트 포인터)에서 MVSWAIT-TIME의 주소 로드.
3. `L 1,0(5)` — MVSWAIT-TIME의 값(4바이트 이진 정수 = 센티초)을 R1에 로드.
4. `ST 1,BINLBL` — 값을 로컬 `DS F` 필드에 저장.
5. `ASMWAIT BINLBL` — z/OS 인터벌 타이머 기동. BINLBL에 저장된 센티초 동안 태스크를 대기 상태로 전환.
6. `LM 14,12,12(13)` — 레지스터 복원.
7. `MVI 12(13),X'FF'` — 세이브 에리어의 특정 바이트를 `X'FF'`로 세팅 (주석에 "ML purpose of this?"로 미확인 표기 — 소스 원작자도 의도 불명 상태).
8. `SR 15,15` + `BR 14` — R15=0(정상 RC)을 세팅하고 복귀.

`MVI 12(13),X'FF'`는 일부 COBOL 런타임 세이브 에리어 관례(back-chain 또는 사용 완료 마커)일 가능성이 있으나 확정 불가 — 소스 주석 자체가 미확인임을 인정하고 있다.

### 현대화 대안

- **Spring Batch**: `Thread.sleep()` 대신 `TaskletStep`을 사용하고, 대기 조건이 자원 준비라면 `@Retryable` + `Backoff` 패턴으로 폴링 대기를 구현하는 것이 더 안전하다.
- **AWS Step Functions**: `Wait` 상태(초 단위 설정)로 완전 대체 가능. 스레드 블로킹 없이 서버리스 방식으로 동일 효과 달성.
- **SQS Delay Queue / EventBridge Scheduler**: 잡 간 의존성 완충이 목적이라면 메시지 지연 전달로 대체 가능.
- **인위적 sleep 자체를 제거**: 선행 잡의 완료를 명시적 파일/DB 상태로 확인(폴링 또는 이벤트 트리거)하는 방식이 더 견고하다. WAITSTEP 류의 하드코딩된 대기는 환경 속도 변화에 취약하다.
