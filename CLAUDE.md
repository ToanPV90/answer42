# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build/Test Commands

- Build project: `./mvnw clean install`
- Run application: `./mvnw spring-boot:run`
- Run all tests: `./mvnw test`
- Run single test: `./mvnw test -Dtest=TestClassName#testMethodName`
- Production build: `./mvnw clean install -Pproduction`

## Code Style Guidelines

Write end to end implementation with no placeholder code or mock objects or backwards compatibility. try and keep the files less then 300 lines of code by writing utili classes and components.  Use supabase mcp to access and review our db. Use our UIConstants file, we perfer external css classes to be put in our theme.  Only use inline styles if you must.  NO PLACEHOLDER CODE. check your work.  DONT USE ANY DEPRECATED METHODS. ALWAYS use LoggingUtil.  All routes should be in the UIConstants.  All View classes must extends Div implements BeforeEnterObserver and add child components to itself and not an extra container Div.

- **Imports**: Organize imports with java/javax first, then third-party, then project imports
- **Naming**: Use camelCase for methods/variables, PascalCase for classes, ALL_CAPS for constants
- **Error Handling**: Use LoggingUtil for errors, throw meaningful exceptions with context
- **Transactions**: Add @Transactional to service methods (readOnly where appropriate)
- **Annotations**: Use Spring annotations (@Service, @Repository, @Autowired, etc.)
- **JPA/Database**: Use UUID for IDs, proper schema naming (answer42 schema), appropriate fetch types
- **Formatting**: 4-space indentation, no tabs, 120 character line limit
- **Documentation**: Add JavaDoc for public methods with @param and @return tags
- **Security**: Never log sensitive data, encode passwords before storing