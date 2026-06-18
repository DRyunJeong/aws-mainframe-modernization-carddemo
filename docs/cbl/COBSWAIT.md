# COBSWAIT — 배치 대기 유틸리티 (센티초 단위 슬립)

- **유형**: 배치 COBOL Program
- **한 줄 요약**: SYSIN으로 받은 센티초(1/100초) 값을 어셈블러 루틴 `MVSWAIT`에 넘겨 해당 시간만큼 프로세스를 블로킹 대기시키는 단일 목적 유틸리티.

---

## 기능 설명

COBSWAIT는 CardDemo 배치 체계에서 **타이밍 제어(인위적 지연 삽입)**를 위해 사용되는 최소 유틸리티 프로그램이다.
JCL EXEC 스텝의 `PARM=` 또는 SYSIN DD를 통해 8자리 숫자 문자열(센티초)을 입력받아,
이를 이진 정수 필드(`PIC 9(8) COMP`)로 변환한 뒤 어셈블러 서브루틴 `MVSWAIT`에 전달한다.
`MVSWAIT`는 z/OS `STIMER` SVC를 내부적으로 호출하여 지정된 인터벌 동안 태스크를 일시 정지시킨다(추측 — MVSWAIT 소스 미제공).

이 프로그램이 필요한 전형적 시나리오:
- 선행 배치 스텝이 파일 잠금(enqueue)을 해제하기 전에 후행 스텝이 너무 빨리 시작하는 경우 인위적 딜레이 삽입.
- Control-M / CA7 같은 스케줄러 JCL에서 특정 자원 준비 대기.

---

## 입력 / 출력

- **입력**:
  - `SYSIN` — ACCEPT 문(라인 36)으로 읽는 8바이트 문자 숫자열.
    예: `00000500` → 500센티초 = 5초 대기.
    JCL에서는 `SYSIN DD *` 인라인 데이터 또는 `PARM=` 방식 모두 사용 가능(ACCEPT FROM SYSIN은 구현에 따라 PARM 해석 가능 — 추측).

- **출력**:
  - 없음. 프로그램은 순수 사이드이펙트(대기)만 발생시킨다.
  - 복귀 코드(Return Code) = 0 (정상) / `MVSWAIT` 오류 시 비정상 종료 가능(추측 — RC 처리 코드 없음).

---

## 의존성

- **COPY (카피북)**: 없음.
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CALL 'MVSWAIT' USING MVSWAIT-TIME` (라인 38) — z/OS 어셈블러 루틴.
    `STIMER WAIT,BINTVL=` SVC를 랩핑한 것으로 추정(추측).
    링크 편집 시 MVSWAIT 오브젝트가 STEPLIB 또는 시스템 링크팩에 존재해야 함.
- **데이터셋/파일/DB 테이블**: 없음.
- **트랜잭션 ID 또는 EXEC PGM**: `EXEC PGM=COBSWAIT` (배치 전용; CICS 트랜잭션 없음).

---

## 핵심 로직 흐름

```
PROCEDURE DIVISION
│
├─ ACCEPT PARM-VALUE FROM SYSIN          ← 8바이트 문자열 수신
│   (예: "00000500")
│
├─ MOVE PARM-VALUE TO MVSWAIT-TIME       ← PIC X(8) → PIC 9(8) COMP
│   (MOVE 묵시적 형변환: 문자열 → 이진 정수)
│
├─ CALL 'MVSWAIT' USING MVSWAIT-TIME     ← BY REFERENCE 전달
│   (MVSWAIT는 센티초 단위 BINTVL로 STIMER 호출 — 추측)
│
└─ STOP RUN                              ← 프로세스 정상 종료
```

**주의 — MOVE 형변환 동작:**
`PARM-VALUE`는 `PIC X(8)` (DISPLAY 문자), `MVSWAIT-TIME`은 `PIC 9(8) COMP` (이진 정수 4바이트).
COBOL `MOVE X→COMP`는 수치 편집 변환을 수행하므로 `"00000500"` → 정수 500이 `MVSWAIT-TIME`에 저장된다.
단, 입력이 숫자가 아닌 문자를 포함하면 정의되지 않은 동작(IBM Enterprise COBOL에서는 DATA EXCEPTION S0C7 ABEND 발생 가능).

---

## Java/현대화 노트

### 1. 직접 대응 Java 코드

```java
/**
 * COBSWAIT 동등 구현 — SYSIN 첫 줄에서 센티초 값을 읽어 Thread.sleep 수행.
 */
public class CobsWait {
    public static void main(String[] args) throws Exception {
        // ACCEPT PARM-VALUE FROM SYSIN
        try (java.util.Scanner sc = new java.util.Scanner(System.in)) {
            String parmValue = sc.nextLine().trim();           // PIC X(8)

            // MOVE PARM-VALUE TO MVSWAIT-TIME
            int centiseconds = Integer.parseInt(parmValue);   // PIC 9(8) COMP

            // CALL 'MVSWAIT' USING MVSWAIT-TIME
            long millis = centiseconds * 10L;                 // 센티초 → 밀리초
            Thread.sleep(millis);
        }
        // STOP RUN → main() return, exit code 0
    }
}
```

### 2. 마이그레이션 주의사항

| COBOL 동작 | Java 동작 | 비고 |
|---|---|---|
| `ACCEPT FROM SYSIN` | `System.in` 첫 줄 읽기 | JCL SYSIN DD → stdin 리다이렉션 |
| `PIC 9(8) COMP` (4바이트 이진) | `int` (4바이트 이진) | 최대값 99,999,999 센티초 ≒ 277시간 — int 범위 충분 |
| 숫자 이외 입력 → S0C7 ABEND | `NumberFormatException` | Java에서는 명시적 예외 처리 필요 |
| `CALL 'MVSWAIT'` BY REFERENCE | `Thread.sleep()` | MVSWAIT 내부 STIMER는 비선점형; Java sleep은 인터럽트 가능 |
| `STOP RUN` RC=0 | `System.exit(0)` 또는 main() 정상 반환 | — |

### 3. 현대화 대안

- **Spring Batch 환경**: `Thread.sleep()` 대신 `TaskletStep`의 `RepeatStatus.FINISHED` + 별도 `@Retryable` 패턴 사용 권장.
- **컨테이너/클라우드 환경**: 인위적 sleep 대신 AWS Step Functions의 `Wait` 상태 또는 SQS Delay Queue로 대체하면 스레드 블로킹 없이 동일 효과 달성 가능.
- **MVSWAIT 소스 미제공**: 현재 `MVSWAIT` 어셈블러 루틴 소스가 없으므로 마이그레이션 시 `Thread.sleep` 대응만으로 충분하나, MVSWAIT가 추가 RC 세팅이나 SMF 레코드 기록 등 부가 기능을 가질 경우 동작 차이가 생길 수 있다(추측).
- **센티초 단위 주의**: z/OS STIMER BINTVL의 단위는 1/100초(센티초)이다. Java의 `Thread.sleep()`은 밀리초 단위이므로 `centiseconds * 10L` 변환이 필수.
