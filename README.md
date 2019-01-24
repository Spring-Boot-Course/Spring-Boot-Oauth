# Spring-Boot-OAuth2

### google.yml 작성

```yaml
google :
  client :
    clientId : 인증정보
    clientSecret: 인증정보
    accessTokenUri: https://accounts.google.com/o/oauth2/token
    userAuthorizationUri: https://accounts.google.com/o/oauth2/auth
    clientAuthenticationScheme: form
    scope: email, profile
  resource:
    userInfoUri: https://www.googleapis.com/oauth2/v2/userinfo
```

### OAuthConfig

```java
@Configuration
@EnableOAuth2Client
public class OAuthConfig {

    private final OAuth2ClientContext oauth2ClientContext;

    public OAuthConfig(OAuth2ClientContext oauth2ClientContext) {
        this.oauth2ClientContext = oauth2ClientContext;
    }

    @Bean
    public Filter ssoFilter() {
        OAuth2ClientAuthenticationProcessingFilter oauth2Filter = new OAuth2ClientAuthenticationProcessingFilter("/login");
        OAuth2RestTemplate oAuth2RestTemplate = new OAuth2RestTemplate(googleClient(), oauth2ClientContext);
        oauth2Filter.setRestTemplate(oAuth2RestTemplate);
        oauth2Filter.setTokenServices(new UserInfoTokenServices(googleResource().getUserInfoUri(), googleClient().getClientId()));

        return oauth2Filter;
    }

    @Bean
    @ConfigurationProperties("google.client")
    public OAuth2ProtectedResourceDetails googleClient() {
        return new AuthorizationCodeResourceDetails();
    }

    @Bean
    @ConfigurationProperties("google.resource")
    public ResourceServerProperties googleResource() {
        return new ResourceServerProperties();
    }

    @Bean
    public FilterRegistrationBean oauth2ClientFilterRegistration(OAuth2ClientContextFilter filter) {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(filter);
        registration.setOrder(-100);
        return registration;
    }
}
```
> Controller를 통해 URL을 생성하지 않아도 Configuraion으로 처리 가능

ssoFilter()에 있는 <b>OAuth2ClientAuthenticationProcessingFilter</b>의 인자 값인 "/login"이 OAuth 로그인 시작 포인트가 된다.

<b>ConfigurationProperties</b>을 통해 google.yml에 포함된 관련 설정 값들은 이름에 맞춰 AuthorizationCodeResourceDetails.java와 ResourceServerProperties.java의 인스턴스 필드에 할당 된다.

### ssoFilter를 Security를 거치도록 설정

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final Filter ssoFilter;

    public SecurityConfig(Filter ssoFilter) {
        this.ssoFilter = ssoFilter;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.antMatcher("/**")
                .authorizeRequests()
                .antMatchers("/", "/h2-console/**", "/favicon.ico", "/login**").permitAll() // "/login**" 옵션 추가
                .anyRequest().authenticated()
                .and().logout().logoutSuccessUrl("/").permitAll()
                .and().headers().frameOptions().sameOrigin()
                .and().csrf().disable()
                .addFilterBefore(ssoFilter, BasicAuthenticationFilter.class); // OAuthConfig에서 생성한 ssoFilter 추가
    }
}
```

---

## 로그인 세션 관리

Database를 세션 저장로소 사용.
*  여러 WAS들 간의 공용 세션을 사용할 수 있는 가장 쉬운 방법

### JdbcSession 적용

```groovy
compile group: 'org.springframework.session', name: 'spring-session-jdbc', version: '2.1.3.RELEASE'
```

```java
@EnableJdbcHttpSession
public class HttpSessionConfig {
}
```

### H2 인메모리 DB 설정

```java
@Configuration
@EnableJdbcHttpSession
public class HttpSessionConfig extends AbstractHttpSessionApplicationInitializer {

    @Bean
    public EmbeddedDatabase dataSource(){
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("org/springframework/session/jdbc/schema-h2.sql")
                .build();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource){
        return new DataSourceTransactionManager(dataSource);
    }
}
```

### GoogleAuthenticationSuccessHandler 작성

```java
@Component
public class GoogleAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private HttpSession httpSession;
    private ObjectMapper objectMapper;

    public GoogleAuthenticationSuccessHandler(HttpSession httpSession, ObjectMapper objectMapper) {
        this.httpSession = httpSession;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        httpSession.setAttribute(SessionConstants.LOGIN_USER, getGoogleUser(authentication)); // 간단한 구글계정 정보를 세션에 저장
        response.sendRedirect("/me");
    }

    private GoogleUser getGoogleUser(Authentication authentication) { // OAuth 인증정보를 통해 GoogleUser 인스턴스 생성
        OAuth2Authentication oAuth2Authentication = (OAuth2Authentication) authentication;
        return objectMapper.convertValue(oAuth2Authentication.getUserAuthentication().getDetails(), GoogleUser.class);
    }
}
```

> AuthenticationSuccessHandler를 구현하여, onAuthenticationSuccess를 오버라이딩. 이때, 구글 계정 정보는 authentication을 통해 얻을 수 있다.
또한, 타입 캐스팅 한 결과(Map 형식)을 Object Mapper를 통해 JSON으로 변환

### 참조
* https://jojoldu.tistory.com/170?category=635883
* https://www.baeldung.com/spring-session-jdbc
