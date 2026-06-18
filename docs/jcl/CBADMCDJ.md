# CBADMCDJ — CardDemo CICS 리소스 정의 등록 잡

- **유형**: JCL
- **한 줄 요약**: DFHCSDUP 유틸리티를 실행해 CardDemo 애플리케이션의 모든 CICS 리소스(LIBRARY, MAPSET, PROGRAM, TRANSACTION)를 CSD(CICS System Definition) 파일에 정의(DEFINE)하고 목록(LIST)을 출력하는 관리자용 1회성 설치 잡

---

## 기능 설명

CBADMCDJ는 CardDemo 애플리케이션을 CICS 환경에 최초로 배포하거나 재설치할 때 실행하는 **CICS 리소스 등록(install) 전용 잡**이다.

CICS에서 프로그램·화면·트랜잭션을 실행하려면 CSD라는 메타데이터 저장소에 리소스가 미리 정의되어 있어야 한다. 이 잡은 DFHCSDUP(IBM CICS 제공 배치 유틸리티)를 통해 SYSIN 스트림 안의 DFHCSDUP 제어문을 실행하여 아래 리소스를 `GROUP(CARDDEMO)`로 묶어 한꺼번에 등록한다.

| 리소스 유형 | 등록 건수 | 대표 항목 |
|---|---|---|
| LIBRARY | 1 | COM2DOLL → `&HLQ..LOADLIB` (로드 모듈 라이브러리) |
| MAPSET | 16 | COSGN00M(로그인), COACTVWS(계정조회), COTRN00S(거래) 등 |
| PROGRAM | 18 | COSGN00C(로그인), COACTVWC(계정조회), COBIL00C(청구), COADM00C(관리자) 등 |
| TRANSACTION | 5 | CCDM(관리자 메뉴), CCT1~CCT4(테스트용) |

> **주의(소스 확인)**: DEFINE MAPSET과 DEFINE PROGRAM에서 계정(ACCOUNT)용 이름과 카드(CARD)용 이름이 동일한 값으로 중복 정의된 항목이 존재한다(예: COACT00S가 두 번, COSGN00M이 두 번). 이는 재정의(override) 목적이 아니라 **소스 상의 복사 오류**로 보인다(추측). DFHCSDUP는 동일 그룹 내 중복 DEFINE을 오류 없이 처리하지만 마지막 정의가 유효하게 된다.

잡이 완료된 후에는 CICS 관리자가 직접 CEDA(온라인 CSD 편집 유틸리티)로 `CEDA INSTALL GROUP(CARDDEMO)`를 실행해야 리소스가 실제 CICS 실행 환경에 활성화된다. 소스 내 주석(`NOTE: INSTALL GROUP(CARDDEMO) - CEDA IN G(CARDDEMO)`)이 이를 명시한다.

재실행 시에는 SYSIN 안의 주석으로 처리된 `DELETE GROUP(CARDDEMO)` 제어문의 주석(`*`)을 제거한 뒤 실행해야 기존 정의를 먼저 삭제하고 재등록할 수 있다(소스 38~42행 참조).

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|---|---|---|
| STEP1 | `EXEC PGM=DFHCSDUP` | IBM CICS CSD 유틸리티: SYSIN의 DEFINE/LIST 제어문을 읽어 CSD 파일에 리소스를 정의하고, 그룹 목록을 OUTDD/SYSPRINT에 출력 |

**STEP1 주요 DD 구성**

| DD명 | 역할 |
|---|---|
| STEPLIB | DFHCSDUP 실행 모듈 위치(`OEM.CICSTS.V05R06M0.CICS.SDFHLOAD`) |
| DFHCSD | 쓰기 대상 CSD 파일(`OEM.CICSTS.DFHCSD`, `DISP=SHR`) |
| OUTDD | DFHCSDUP 출력(LIST 결과) → SYSOUT |
| SYSPRINT | 유틸리티 메시지 → SYSOUT |
| SYSIN | 인라인 DFHCSDUP 제어문 스트림(`SYMBOLS=JCLONLY`로 JCL 심볼 치환 활성화) |

**PARM 옵션** (`PARM='CSD(READWRITE),PAGESIZE(60),NOCOMPAT'`)

- `CSD(READWRITE)`: CSD를 읽기/쓰기 모드로 열기(DEFINE 수행에 필수)
- `PAGESIZE(60)`: LIST 출력의 페이지 행수
- `NOCOMPAT`: 이전 CICS 릴리스와의 호환 모드 비활성화

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 단일 인라인 스텝으로 PROC 참조 없음

- **호출 프로그램 (EXEC PGM)**:
  - `DFHCSDUP` — IBM CICS 제공 CSD 배치 유틸리티. `OEM.CICSTS.V05R06M0.CICS.SDFHLOAD` 라이브러리에서 로드. CICS 설치 없이는 실행 불가

- **데이터셋/파일/DB 테이블**:
  - `OEM.CICSTS.DFHCSD` — CICS 리소스 정의 파일(CSD). 이 잡의 정의 대상. 모든 CICS 환경에서 사전에 할당되어 있어야 함(`DISP=SHR`)
  - `OEM.CICSTS.V05R06M0.CICS.SDFHLOAD` — CICS 로드 라이브러리(STEPLIB). DFHCSDUP 실행 모듈 포함
  - `&HLQ..LOADLIB` (`HLQ=AWS.M2.CARDDEMO` → `AWS.M2.CARDDEMO.LOADLIB`) — CardDemo 로드 모듈 PDS. DEFINE LIBRARY 문에서 CICS에 알려줄 실행 모듈 위치로 지정됨. 이 잡이 실행될 때 실제로 열리지는 않으며, CSD에 경로 정보만 기록됨

- **선행/후행 잡**:
  - **선행**: `CBADMCDJ` 실행 전에 CardDemo COBOL 소스를 컴파일하고 `AWS.M2.CARDDEMO.LOADLIB`에 링크-에디트(LKED)하는 컴파일 잡이 완료되어 있어야 한다. 소스를 먼저 빌드해야 DFHCSDUP가 등록한 PROGRAM 정의가 실제 모듈을 가리킬 수 있음
  - **후행**: 잡 완료 후 CICS 온라인에서 `CEDA INSTALL GROUP(CARDDEMO)` 수동 실행 필요. 또는 CICS 리전 재기동 시 AUTO-INSTALL 설정에 따라 자동 활성화될 수 있음(추측). 이후 `COSGN00C`(트랜잭션 CC00)으로 로그인 가능해짐

---

## Java/현대화 노트

### DFHCSDUP / CSD의 Java 대응 개념

CSD는 CICS 런타임이 프로그램·트랜잭션을 알아보기 위한 **서비스 레지스트리(service registry)**이다. Java 생태계에서 가장 가까운 유사체는 다음과 같다.

```
CSD (CICS System Definition)  ≈  Spring ApplicationContext 빈 정의
                               또는 서블릿 컨테이너의 web.xml / @WebServlet
                               또는 Kubernetes의 Service/Deployment YAML
```

- **DEFINE PROGRAM(COSGN00C) TRANSID(CC00)** — `@RestController` + `@RequestMapping("/CC00")`에 해당. 특정 트랜잭션 ID를 특정 프로그램(핸들러)에 라우팅
- **DEFINE MAPSET(COSGN00M)** — BMS 맵셋은 HTML 템플릿/Thymeleaf 뷰와 유사. MAPSET 정의는 뷰 파일의 위치를 컨테이너에 등록하는 것
- **DEFINE LIBRARY(COM2DOLL)** — 클래스패스(classpath) 또는 `java -jar` 실행 파일 경로 등록에 대응. CICS가 프로그램 실행 모듈을 어느 LOADLIB에서 찾을지 알려줌
- **DEFINE TRANSACTION(CCDM) PROGRAM(COADM00C)** — 트랜잭션 ID와 프로그램의 분리는 URL 패턴과 컨트롤러 빈의 분리와 동일한 관심사 분리(SoC) 구조
- **GROUP(CARDDEMO)** — Spring의 `@ComponentScan` 패키지, 또는 Maven 모듈 단위와 유사한 리소스 그룹. `CEDA INSTALL GROUP(CARDDEMO)` 한 번으로 그룹 내 전체 리소스를 활성화하는 것은 `ApplicationContext.refresh()`에 대응

### 현대화 시 주의사항

1. **CSD → 배포 자동화로 전환**: 마이그레이션 후 이 잡에 해당하는 작업은 CI/CD 파이프라인(GitHub Actions, AWS CodeDeploy 등)의 배포 스텝으로 대체된다. 수동 `CEDA INSTALL` 단계 없이 코드 배포 자체가 리소스 등록을 완료해야 한다.

2. **중복 DEFINE 문제**: 소스 50~72행에서 COSGN00M, COACT00S, COACTVWS, COACTUPS, COACTDES가 각각 두 번씩 DEFINE되어 있다. DFHCSDUP는 중복 정의를 에러 없이 처리하지만(마지막 값 우선), Java 마이그레이션 분석 도구가 리소스 목록을 추출할 때 중복 카운트가 발생할 수 있으므로 정규화가 필요하다.

3. **TRANSID 누락 PROGRAM**: `COACT00C`, `COACTVWC`, `COACTUPC`, `COACTDEC`, `COTRN00C`, `COTRNVWC`, `COTRNVDC`, `COTRNATC`, `COBIL00C` 등 다수 PROGRAM이 TRANSID 없이 등록된다. 이 프로그램들은 다른 트랜잭션에서 `EXEC CICS XCTL PROGRAM(...)` 또는 `EXEC CICS LINK PROGRAM(...)` 방식으로만 호출되며 직접 트랜잭션으로 기동되지 않는다. Java로는 `@Service` 빈(직접 HTTP 엔드포인트 없이 내부 호출만 받는 서비스)에 해당한다.

4. **테스트 트랜잭션 CCT1~CCT4**: `COTSTP1C`~`COTSTP4C` 및 대응 MAPSET은 테스트 목적 더미 프로그램이다(소스 89~96행, 134~157행). 현대화 범위에서 제외하거나 통합 테스트 픽스처로 대체 가능하다.

5. **HLQ 파라미터**: 소스 25행 `SET HLQ=AWS.M2.CARDDEMO`는 JCL 심볼로, 환경별(개발/QA/운영) 데이터셋 HLQ가 다를 경우 이 값만 변경하면 모든 DSN이 일괄 변경된다. Java 마이그레이션 시 application.properties / AWS Systems Manager Parameter Store의 환경별 설정값과 동일한 역할이다.
