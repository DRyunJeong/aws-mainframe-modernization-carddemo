# COBOL → Java 마이그레이션 전략 (실행 핸드오프)

> Claude Code가 마이그레이션 작업 시 따르는 전략 문서. CardDemo COBOL 배치 프로그램을 Java로 이주한다.
> 첫 대상: `app/cbl/CBACT04C.cbl` (이자 계산 배치). 도메인: 규제 금융 — 정확성·정밀도 최우선.
> 산출물 위치: `app/java/` 아래. 설명/문서는 한국어, 코드 식별자·타입명은 원문 유지.

## 0. 불변 원칙

1. **동작 보존 + bit-for-bit.** 레거시의 현재 동작이 곧 스펙. 금액·이자·잔액은 **허용오차 없는 완전 일치**.
2. **정답지(oracle) = 돌아가는 원본 COBOL** (GnuCOBOL 실행 결과). **Claude의 코드 해석은 정답이 아니다**(순환 금지). 기존 `docs/`·`ARCHITECTURE_kr.md`는 *이해 보조*일 뿐 정답 근거가 아니며, 정확성은 항상 *원본 실행과의 대조*로 확인한다.
3. **source of truth = 테스트/하니스.** 생성된 Java는 재생성 가능한 산물. 잠가둘 것은 테스트·하니스다.

## 1. 마이그레이션 절차 (프로그램 1개, 5단계 — 먼저 plan 모드로 설계)

1. **프로젝트 구조** 생성 (예: `app/java/batch_processing_workflow`).
2. **copybook → 공유 Java 타입**(DTO/record) **먼저**. 여러 프로그램이 공유하는 데이터 구조이므로 재사용 가능하게.
3. **원본 파일 포맷 호환 IO 레이어** (고정폭/PIC, EBCDIC, packed/COMP-3 바이트).
4. **비즈니스 로직 변환**, COBOL 특유 동작 보존(4장).
5. **이중 테스트 하니스**: GnuCOBOL(원본) + Java 17을 같은 입력에 돌려 결과 diff.

## 2. 검증 (가장 중요)

- **골든 마스터**: 원본 COBOL을 GnuCOBOL로 컴파일·실행해 출력 캡처 = 정답. Java를 같은 입력에 돌려 비교.
- **원본이 그대로 실행 불가하면**(순차/VSAM 파일 의존 등): *비즈니스 로직은 손대지 말고* 파일 I/O만 테스트 데이터에 맞게 각색해 GnuCOBOL에서 돌아가게 만든다.
- **입력 데이터**: 제공된 sample data 우선. 없거나 부족하면 합성 + 엣지 케이스(경계 금액, 음수, 최대 자릿수, 0, 윤년/월말 등).
- **비교 범위**: 최종 출력 + (핵심 산술은) **중간 계산값·파일 작성 내용·각 변환 단계**까지 bit-for-bit. 비교 전 비결정 필드(타임스탬프·실행ID 등) 정규화.
- **완료 기준**: 모든 (sample+합성) 케이스에서 금액 차이 0, 엣지 케이스 커버, 불일치는 추적·해결.

## 3. 아키텍처 방침

- 이번 연습 프로그램은 데모대로 **idiomatic Java** 로 OK.
- 단 *원칙*: 첫 규제 이주는 **like-for-like(동작 보존) 먼저 → 이후 테스트 보호 아래 리팩터링**이 저위험(재설계가 많을수록 검증할 변형↑). 실 프로젝트에서 재확인할 결정 사항.
- (스케일 시) 일관성: 금액=`BigDecimal` 규칙을 ArchUnit으로 강제 + lint/format. 프로그램 1개엔 과하면 생략.

## 4. COBOL → Java 함정 (반드시 보존/주의)

- **십진수 (★최우선)**: COMP-3/존 십진수 → `BigDecimal` + 정확한 scale. **COBOL `COMPUTE`는 기본 truncate**(CBACT04C의 `/1200` 등) → 동일 결과엔 `RoundingMode.DOWN`. 정책상 반올림이면 의도적으로 바꾸되 **차이를 문서화**. `double`/`float` 금지.
- **고정폭/PIC**: 컬럼 위치 파싱, 공백 패딩 trim 정책. 부호: `S9` DISPLAY는 overpunch, `COMP-3`은 마지막 니블.
- **REDEFINES**: Java에 union 없음 → `ByteBuffer` 뷰 또는 별도 클래스 + 변환. **OCCURS** 상한 → `List` + 경계검증. **88-level** → enum/predicate.
- **제어흐름**: `PERFORM`/`GO TO` → 메서드/루프. paragraph fall-through 주의.
- **날짜**: 문자열(YYYYMMDD 등) → `LocalDate`/`DateTimeFormatter`, 경계(월말/윤년/타임존) 명시.
- **인코딩**: EBCDIC ↔ ASCII (packed/binary는 단순 텍스트 변환으로 깨지니 필드별 처리).

## 5. 운영 (Claude Code)

- **plan 모드로 먼저** 전체 전략을 세운다(성급히 파일 편집 금지). 데모도 그렇게 했다.
- **effort 차등**: 복잡 로직(이자 계산 등) high/xhigh, 단순·IO medium. 전부 max 금지.
- 끊기면 **"이어서"**(재실행 금지), allowlist 미리 등록, 진행분은 파일로 증분 저장.
- `docs/`·`ARCHITECTURE_kr.md`가 있으면 *이해의 출발점*으로 참고하되, 정답은 원본 실행으로 확정한다.