package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
@Rollback(value = false)
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em); //동시성 문제 없음
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라.
        String qlString =
                "select m from Member m " +
                " where m.username = :username";

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1") //파라미터 바인딩 처리
                .getSingleResult();

        assertEquals(findMember.getUsername(), "member1");
    }

    @Test
    public void startQuerydsl() {
//        QMember m = new QMember("m"); //별칭 내부조인을 할 때만 사용하면 되고 그 외엔 아래와 같이 사용하는 것을 권장
//        QMember m = QMember.member; // 기본 인스턴스 사용

        Member findMember = queryFactory
                .select(member)//<권장> QMember.member 를 static import하여 깔끔하게 사용 가능하다.
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertEquals(findMember.getUsername(), "member1");

        // Querydsl 의 장점
        // 컴파일 시점 오류를 잡아준다! (런타임 오류를 방지할 수 있음)
        // 파라미터 바인딩을 자동으로 해결해준다.
    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.between(10, 30)))
                .fetchOne();

        assertEquals(findMember.getUsername(), "member1");
    }

    @Test
    public void searchParam() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"), // .and()는 ','로도 사용할 수 있음, 동적쿼리에 유리함
                        (member.age.between(10, 30))
                )
                .fetchOne();

        assertEquals(findMember.getUsername(), "member1");
    }

    @Test
    public void resultFetch() {
//        List<Member> fetch = queryFactory
//                .selectFrom(member)
//                .fetch(); //fetch() : 리스트 조회, 데이터 없으면 빈 리스트 반환
//
//        Member fetchOne = queryFactory
//                .selectFrom(member)
//                .fetchOne(); //fetchOne() : 단 건 조회, 결과가 없으면 : null, 결과가 둘 이상이면 : com.querydsl.core.NonUniqueResultException
//
//        Member fetchFirst = queryFactory
//                .selectFrom(member)
//                .fetchFirst(); //fetchFirst() : limit(1).fetchOne()

        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults(); //fetchResults() : 페이징 정보 포함, total count 쿼리 추가 실행

        results.getTotal();
        List<Member> content = results.getResults();

        long total = queryFactory
                .selectFrom(member)
                .fetchCount(); //fetchCount() : count 쿼리로 변경해서 count 수 조회
    }

    /*
    * 회원 정렬 순서
    * 1. 회원 나이 내림차순(desc)
    * 2. 회원 이름 올림차순(asc)
    * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
    */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast()) //nullsFirst()
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertEquals(member5.getUsername(), "member5");
        assertEquals(member6.getUsername(), "member6");
        assertNull(memberNull.getUsername());
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertEquals(result.size(), 2);
    }
    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertEquals(queryResults.getTotal(), 4);
        assertEquals(queryResults.getLimit(), 2);
        assertEquals(queryResults.getOffset(), 1);
        assertEquals(queryResults.getResults().size(), 2);

        //fetchResults()를 사용하면 count() 쿼리까지 나가는데, query문이 복잡해지만 count()문도 불필요하게 복잡해질 수 있기 때문에
        //분리하여 사용하는 것이 좋다.
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertEquals(tuple.get(member.count()), 4);
        assertEquals(tuple.get(member.age.sum()), 100);
        assertEquals(tuple.get(member.age.avg()), 25);
        assertEquals(tuple.get(member.age.max()), 40);
        assertEquals(tuple.get(member.age.min()), 10);

        // 여러 데이터타입을 반환하기 때문에 Tuple이라는 타입으로 모아온다.
        // 실무에선 Tuple보다는 필요한 Dto를 만들어 가져온다.
    }

    /*
    * 팀의 이름과 각 팀의 평균 연령을 구해라*/
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertEquals(teamA.get(team.name), "teamA");
        assertEquals(teamA.get(member.age.avg()), 15);
        assertEquals(teamB.get(team.name), "teamB");
        assertEquals(teamB.get(member.age.avg()), 35);
    }

    /*
    * 팀 A에 소속된 모든 회우너*/
    @Test
    public void join() throws Exception {
        //given
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");

    }

    /*
    * 세타 조인
    * 회원의 이름이 팀 이름과 같은 회원 조회
    * */
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }
    // 세타 조인
    // from 절에 여러 엔티티를 선택해서 세타 조인
    // 외부 조인 불가능 -> on절을 사용하면 외부 조인 가능

    /*
    * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
    * JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
    * */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

        // inner join인 경우의 on절은 where절과 똑같기 때문에 익숙한 where절로 하는 것이 좋다.
        // outer join은 on절이 있어야만 결과값이 나오기 때문에 on절로 해야한다.
    }

    /*
    * 연관관계 없는 엔티티 외부 조인
    * 회원의 이름이 팀 이름과 같은 대상 외부 조인
    * */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team)
                .on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }


// 검색 조건
//member.username.eq("member1") // username = 'member1'
//member.username.ne("member1") //username != 'member1'
//member.username.eq("member1").not() // username != 'member1'
//
//member.username.isNotNull() //이름이 is not null
//
//member.age.in(10, 20) // age in (10,20)
//member.age.notIn(10, 20) // age not in (10, 20)
//member.age.between(10,30) //between 10, 30
//
//member.age.goe(30) // age >= 30  greater or equal
//member.age.gt(30) // age > 30
//member.age.loe(30) // age <= 30  lower or eqal
//member.age.lt(30) // age < 30
//
//member.username.like("member%") //like 검색
//member.username.contains("member") // like ‘%member%’ 검색
//member.username.startsWith("member") //like ‘member%’ 검색

    @PersistenceUnit
    EntityManagerFactory emf;
    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoin() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() //.join() 뒤에 fetchJoin()을 붙여주면 된다.
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isTrue();
    }

    /*
    * 나이가 가장 많은 회원 조회
    * */
    @Test
    public void subQuery() {

        QMember memberSub = new QMember("memberSub"); // 내부조인시에는 별칭이 달라야 하므로..

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        //JPAExpressions 서브쿼리절 static import 로 생략
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /*
     * 나이가 평균 이상인 회원 조회
     * */
    @Test
    public void subQueryGoe() {

        QMember memberSub = new QMember("memberSub"); // 내부조인시에는 별칭이 달라야 하므로..

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        // 서브쿼리절
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /*
     * 나이가 가장 많은 회원 조회
     * */
    @Test
    public void subQueryIn() {
        QMember memberSub = new QMember("memberSub"); // 내부조인시에는 별칭이 달라야 하므로..

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        // 서브쿼리절
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /*
     * 나이가 가장 많은 회원 조회
     * */
    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub"); // 내부조인시에는 별칭이 달라야 하므로..

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }
    //    from 절의 서브쿼리 한계
//    JPA JPQL 서브쿼리의 한계점으로 from 절의 서브쿼리(인라인 뷰)는 지원하지 않는다. 당연히 Querydsl도 지원하지
//        않는다. 하이버네이트 구현체를 사용하면 select 절의 서브쿼리는 지원한다. Querydsl도 하이버네이트 구현체를 사용
//        하면 select 절의 서브쿼리를 지원한다.
//        from 절의 서브쿼리 해결방안
//        1. 서브쿼리를 join으로 변경한다. (가능한 상황도 있고, 불가능한 상황도 있다.)
//        2. 애플리케이션에서 쿼리를 2번 분리해서 실행한다.
//        3. nativeSQL을 사용한다

    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }

    }
    // switch문으로 db에서 조작하는것보다 화면이나 애플리케이션에서 하는 것이 효율이 좋다.

    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() {

        //{username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    //.stringValue() 특히 ENUM 처리할 때 자주 사용함!!

    // 프로젝션: select 대상 지정
    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
        // Tuple이 Repository까지만 쓰고 Controller 단이나 service 계층은 Dto로 바꿔서 쓰는 것이 좋다.
        // Querydsl이나 JPA같은 하부 구현 기술은 앞단에서 아는것은 좋은 설계가 아니다.
        // repository와 dto까지만 써야지 나중에 querydsl이 다른것으로 바뀌더라도 앞단 서비스까지 바꿀 필요가 없어진다. 의존성을 낮춤
    }

    @Test
    public void findDtoByJQPL() {
        List<MemberDto> resultList = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : resultList) {
            System.out.println("memberDto = " + memberDto);
        }
        //순수 JPA에서 DTO를 조회할 때는 new 명령어를 사용해야함
        //DTO의 package이름을 다 적어줘야해서 지저분함
        //생성자 방식만 지원함(setter 안됨)
    }

    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        // setter와 기본 생성자가 필요함
    }

    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        // setter가 필요 없이 바로 필드에 값을 주입함.
    }

    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        // 생성자의 인자 타입이 맞아야한다.
    }

    @Test
    public void findDtoByConstructor2() {
        List<UserDto> result = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
        // 필드명은 관계없이 생성자의 인자 타입이 맞아야한다.
    }

    @Test
    public void findUserDto() {
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        member.age))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
        // 필드값이 달라질 경우 .as("") 에 해당 필드값을 넣어주면 된다.
    }

    @Test
    public void findUserDto2() {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),

                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
        // 서브쿼리의 결과를 필드에서 매칭하여 주입하는 방법
        //프로퍼티나, 필드 접근 생성 방식에서 이름이 다를 때 해결 방안
        //ExpressionUtils.as(source,alias) : 필드나, 서브 쿼리에 별칭 적용
        //username.as("memberName") : 필드에 별칭 적용
    }

    @Test
    void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        // 생성자Projection은 오류를 컴파일 단계에서 못잡지만,
        // @QueryProjection을 사용하면 컴파일 오류를 잡아준다. 파라미터 힌트도 줌.
        // 단점
        // 1. QClass를 생성해야됨
        // 2. dto(@QueryProjection)가 querydsl에 의존성을 가지게 됨.
    }

    @Test
    void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertEquals(result.size(), 1);


    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertEquals(result.size(), 1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory.
                selectFrom(member)
//                .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond, ageCond)) // 조립
                .fetch();
        // where()에 null이 들어가면 무시함. -> 동적쿼리 가능
        // 조립 가능함 ! 재사용 가능 !! -> 가독성 상승
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
        // 이것만 쓰면 null 처리 따로 해줘야함
    }


    @Test
    void bulkUpdate() {

        //member1 = 10 -> DB Member1
        //member2 = 20 -> DB Member2
        //member3 = 30 -> DB Member3
        //member4 = 40 -> DB Member4

        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();

        //member1 = 10 -> DB 비회원
        //member2 = 20 -> DB 비회원
        //member3 = 30 -> DB Member3
        //member4 = 40 -> DB Member4

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }

        //bulk 연산은 영속성 컨텍스트를 거치지 않고 바로 DB에 가기 때문에 서로 불일치된다.
        //따라서 위와 같이 조회를 통해 db에서 변경된 값을 가져와도
        //영속성 컨텍스트에 기존 값이 올라가 있으면 영속성 컨텍스트가 우선시되어
        //DB값을 버리고 영속성 컨텍스트에 담긴 변경 전 값이 나오게 된다.  -> '리피티블 리드' 라고 함.
        // -> em.flush(); em.clear(); 로 bulk연산 후엔 항상 초기화 시켜줘야 한다.

    }

    @Test
    void bulkAdd() {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) //minus()가 없으므로 add(-5)로 사용
                .execute();
    }

    @Test
    void bulkDelete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    @Test
    void sqlFunction() {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(Expressions.stringTemplate(
//                        "function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
        // 일반적으로 db에 제공되는것은 거의다 내장하고 있음
    }
}
