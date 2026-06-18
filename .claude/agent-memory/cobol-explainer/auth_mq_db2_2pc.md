---
name: auth-mq-db2-2pc
description: Pending-Authorization 모듈의 MQ 트리거 처리기 COPAUA0C와 사기표시 2PC 흐름(COPAUS1C 오케스트레이터 + COPAUS2C DB2). IMS+DB2를 EXEC CICS SYNCPOINT로 원자 커밋하는 방식, MQ가 syncpoint 밖에 있는 점.
metadata:
  type: project
---

선택 모듈 app/app-authorization-ims-db2-mq/cbl. IMS(HIDAM 구조는 [[ims-hierarchical-db]])+DB2+MQ 협력. README 44행이 "Two-phase commit transactions across IMS DB and DB2"로 의도 명시.

== COPAUA0C (트랜잭션 CP00, MQ 트리거 백그라운드 처리기, BMS 없음) ==
MAIN-PARA(220행): 1000-INITIALIZE → 2000-MAIN-PROCESS → 9000-TERMINATE → RETURN. 화면 없는 메시지 구동 데몬 (Java로는 @JmsListener / MDB).
- 1000-INITIALIZE(230행): EXEC CICS RETRIEVE INTO(MQTM) — MQ 트리거가 넘긴 MQTM(트리거 메시지)에서 큐명/트리거데이터 회수. 그다음 1100-OPEN-REQUEST-QUEUE(MQOPEN, 262행)로 요청 큐 열고 3100으로 첫 메시지 읽음.
- 2000-MAIN-PROCESS(323행): PERFORM UNTIL no-msg OR loop-end 루프. 메시지 1건당:
    2100-EXTRACT-REQUEST-MSG(UNSTRING으로 CSV 파싱) → 5000-PROCESS-AUTH → ADD 1 처리카운트 → **EXEC CICS SYNCPOINT(335행)** → 한도(WS-REQSTS-PROCESS-LIMIT) 초과면 종료, 아니면 3100으로 다음 MQGET.
    핵심: 메시지마다 처리 후 즉시 SYNCPOINT = 메시지 단위 커밋(배치 한 번에 몰지 않음). 멱등성/at-least-once 의미 주의.
- 3100-READ-REQUEST-MQ(386행): CALL 'MQGET'. **MQGMO-NO-SYNCPOINT**(389행)+MQGMO-WAIT+CONVERT+FAIL-IF-QUIESCING. 즉 MQGET은 UOW 밖(비트랜잭션 읽기). MQRC-NO-MSG-AVAILABLE면 NO-MORE-MSG로 루프종료.
- 5000-PROCESS-AUTH(438행) 순서: 1200-SCHEDULE-PSB(EXEC DLI SCHD로 PSB 스케줄, 온라인 IMS는 매번 SCHD/TERM) → 5100 XREF READ(VSAM, EXEC CICS READ) → 5200 ACCT READ → 5300 CUST READ → 5500-READ-AUTH-SUMMRY(EXEC DLI GU PAUTSUM0 WHERE ACCNTID) → 6000-MAKE-DECISION → 7100-SEND-RESPONSE(MQPUT) → CARD 있으면 8000-WRITE-AUTH-TO-DB.
- 6000-MAKE-DECISION(657행) = 핵심 비즈니스 규칙: 가용신용 WS-AVAILABLE-AMT = CREDIT-LIMIT - CREDIT-BALANCE (요약세그 있으면 PA-* 누적값, 없으면 ACCT 마스터값 사용). 거래액 > 가용이면 DECLINE+INSUFFICIENT-FUND. 카드없음/계좌없음도 DECLINE. resp code '00'승인/'05'거절, reason 3100(not found)/4100(funds)/4200(card inactive)/4300(closed)/5100(card fraud)/5200(merchant fraud)/9000. 응답은 CSV로 STRING해서 W02-PUT-BUFFER.
- 7100-SEND-RESPONSE(738행): CALL 'MQPUT1'(758행). 응답큐=요청메시지의 MQMD-REPLYTOQ, CORRELID=요청 MSGID 보존(745행 WS-SAVE-CORRELID) = 표준 request/reply 상관. **MQPMO-NO-SYNCPOINT**(753행), NOT-PERSISTENT, EXPIRY 50. 응답도 UOW 밖.
- 8000-WRITE-AUTH-TO-DB(786행): 8400-UPDATE-SUMMARY + 8500-INSERT-AUTH.
    8400(798행): 요약세그 없으면 INITIALIZE 후 ISRT, 있으면 누적 갱신 후 REPL(PAUTSUM0 루트). 승인시 카운트/금액 누적 + CREDIT-BALANCE에 가산(=가용한도 차감 효과).
    8500(854행): 상세 PAUTDTL1 ISRT. 특이: 자식 삽입을 EXEC DLI ISRT에 SEGMENT(PAUTSUM0) WHERE(ACCNTID=) + SEGMENT(PAUTDTL1) FROM(...) 식 2단 경로로 부모 위치+자식 삽입을 한 명령에 묶음(913행). 타임스탬프는 99999-YYDDD, 999999999-시간 식 "보수(complement)"로 저장 → IMS 키 정렬 시 최신이 먼저 오게 하는 트릭.
9000-TERMINATE(940행): IMS-PSB-SCHD면 EXEC DLI TERM, MQCLOSE.
=> COPAUA0C의 "트랜잭션 경계": IMS 쓰기(ISRT/REPL)만 CICS UOW 안 → 335행 SYNCPOINT가 IMS만 커밋. MQ GET/PUT은 전부 NO-SYNCPOINT라 UOW 밖(독립). 따라서 COPAUA0C 자체는 IMS 단일리소스 커밋이며 "MQ까지 묶은 2PC"가 아님 — 이 점이 비자명.

== 사기표시 2PC (진짜 IMS+DB2 원자성은 여기) ==
오케스트레이터는 COPAUS2C가 아니라 **COPAUS1C** (상세화면). 사용자 전제("COPAUS2C 한 프로그램에 EXEC DLI+EXEC SQL 공존")는 사실과 다름:
- COPAUS2C(244행, 짧음)는 **DB2 전용**. EXEC SQL INSERT INTO CARDDEMO.AUTHFRDS(142행), -803(중복키)면 FRAUD-UPDATE로 UPDATE(223행). EXEC DLI 전혀 없음. SYNCPOINT/RETURN도 안 함 — EXEC CICS RETURN(218행)만. 즉 "DB2 변경만 하고 커밋은 호출자에 위임"하는 순수 워커. (DB2 호스트변수는 dcl/AUTHFRDS.dcl, 테이블 ddl/AUTHFRDS.ddl, PK=CARD_NUM+AUTH_TS)
- COPAUS1C MARK-AUTH-FRAUD(230행, PF5/DFHPF5 진입): READ-AUTH-RECORD(GHU로 세그 읽음) → fraud 플래그 토글 → **EXEC CICS LINK PROGRAM(COPAUS2C) COMMAREA(WS-FRAUD-DATA)**(248행)로 DB2 INSERT 위임 → 결과 검사(253행):
    LINK 정상 & WS-FRD-UPDT-SUCCESS → UPDATE-AUTH-DETAILS(520행)에서 **EXEC DLI REPL PAUTDTL1**(525행)로 IMS에 fraud 표시 → REPL 성공시 **TAKE-SYNCPOINT(557행: EXEC CICS SYNCPOINT)** = DB2 INSERT + IMS REPL 동시 커밋.
    LINK 실패 OR DB2 실패 OR IMS REPL 실패 → **ROLL-BACK(565행: EXEC CICS SYNCPOINT ROLLBACK)** = 둘 다 롤백.
=> 진짜 2PC 시연: 두 리소스(IMS DB, DB2)가 단일 CICS UOW에 등록되고, 하나의 EXEC CICS SYNCPOINT가 양쪽을 원자 커밋, 하나의 SYNCPOINT ROLLBACK이 양쪽을 원자 롤백. CICS가 트랜잭션 코디네이터(=Java JTA TransactionManager). EXEC SQL/EXEC DLI는 XAResource enlist에 해당.

== 2PC 메커니즘 핵심 (모던 매핑) ==
- CICS UOW = 분산 트랜잭션. RM 2개 = IMS DB(RDO RMI), DB2(DB2ENTRY/플랜, README 177행 DB201PLN). EXEC CICS SYNCPOINT = prepare+commit 두 페이즈를 CICS가 RM들에 조율 (1단계 vote, 2단계 commit). 프로그래머는 SYNCPOINT 한 줄만 — XA 프로토콜 자체는 CICS가 가림.
- Java 대응: CICS=JTA `TransactionManager`/Atomikos·Narayana, EXEC CICS SYNCPOINT = `UserTransaction.commit()` 또는 Spring `@Transactional` 메서드 정상종료, ROLLBACK = `setRollbackOnly()`/예외. IMS·DB2 각각이 `XAResource`. COPAUS1C가 LINK로 워커 부르고 자기가 커밋 = "한 @Transactional 메서드 안에서 두 DAO 호출 후 메서드 끝에서 일괄 커밋".
- 주의: MQ는 위 2PC에 안 들어감. COPAUA0C의 MQGET/PUT은 NO-SYNCPOINT(비트랜잭션). 운영 MQ-IMS XA를 원하면 MQGMO-SYNCPOINT로 바꿔야 함 — 데모는 단순화. 모던 전환 시 JMS+DB를 한 XA/JTA로 묶을지(메시지 유실 vs 중복) 설계 결정 필요. 현재는 at-least-once 처리 후 메시지단위 IMS 커밋 → 멱등성 키(CARD_NUM+AUTH_TS) 활용 권장.

관련: [[ims-hierarchical-db]](EXEC DLI/PCB/상태코드 상세), [[online-pseudoconv-pattern]](CICS 골격/LINK/COMMAREA), [[copybook-record-layouts]](CCPAURQY/CCPAURLY/CIPAUSMY/CIPAUDTY).
