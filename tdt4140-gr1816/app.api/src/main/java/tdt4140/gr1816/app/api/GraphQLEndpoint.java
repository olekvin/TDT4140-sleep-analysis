package tdt4140.gr1816.app.api;

import com.coxautodev.graphql.tools.SchemaParser;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import graphql.ErrorType;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.servlet.GraphQLContext;
import graphql.servlet.SimpleGraphQLServlet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import tdt4140.gr1816.app.api.auth.AuthContext;
import tdt4140.gr1816.app.api.resolvers.DataAccessRequestResolver;
import tdt4140.gr1816.app.api.resolvers.MessageResolver;
import tdt4140.gr1816.app.api.resolvers.Mutation;
import tdt4140.gr1816.app.api.resolvers.PulseDataResolver;
import tdt4140.gr1816.app.api.resolvers.Query;
import tdt4140.gr1816.app.api.resolvers.SigninResolver;
import tdt4140.gr1816.app.api.resolvers.SleepDataResolver;
import tdt4140.gr1816.app.api.resolvers.StepsDataResolver;
import tdt4140.gr1816.app.api.types.User;

@WebServlet(urlPatterns = "/graphql")
public class GraphQLEndpoint extends SimpleGraphQLServlet {

  public static final UserRepository userRepository;
  public static final SleepDataRepository sleepDataRepository;
  public static final DataAccessRequestRepository dataAccessRequestRepository;
  public static final StepsDataRepository stepsDataRepository;
  public static final PulseDataRepository pulseDataRepository;
  public static final MessageRepository messageRepository;

  public static MongoDatabase mongo;

  static {
    String dbname = System.getenv("DB_NAME");
    String host = System.getenv("MONGO_HOST");
    mongo =
        new MongoClient(host == null ? "localhost" : host)
            .getDatabase(dbname == null ? "gruppe16" : dbname);
    userRepository = new UserRepository(mongo.getCollection("users"));
    sleepDataRepository = new SleepDataRepository(mongo.getCollection("sleepData"));
    stepsDataRepository = new StepsDataRepository(mongo.getCollection("stepData"));
    pulseDataRepository = new PulseDataRepository(mongo.getCollection("pulseData"));
    dataAccessRequestRepository =
        new DataAccessRequestRepository(mongo.getCollection("dataAccessRequests"));
    messageRepository = new MessageRepository(mongo.getCollection("messages"));
  }

  public GraphQLEndpoint() {
    super(buildSchema());
  }

  public static GraphQLSchema buildSchema() {
    return SchemaParser.newParser()
        .file("schema.graphqls")
        .resolvers(
            new Query(
                userRepository,
                sleepDataRepository,
                stepsDataRepository,
                pulseDataRepository,
                dataAccessRequestRepository,
                messageRepository),
            new Mutation(
                userRepository,
                sleepDataRepository,
                stepsDataRepository,
                pulseDataRepository,
                dataAccessRequestRepository,
                messageRepository),
            new SigninResolver(),
            new DataAccessRequestResolver(userRepository),
            new SleepDataResolver(userRepository),
            new StepsDataResolver(userRepository),
            new PulseDataResolver(userRepository),
            new MessageResolver(userRepository))
        .build()
        .makeExecutableSchema();
  }

  // Check the token of the User and return the users id
  @Override
  protected GraphQLContext createContext(
      Optional<HttpServletRequest> request, Optional<HttpServletResponse> response) {
    User user =
        request
            .map(req -> req.getHeader("Authorization"))
            .filter(id -> !id.isEmpty())
            .map(id -> id.replace("Bearer ", ""))
            .map(userRepository::findById)
            .orElse(null);
    return new AuthContext(user, request, response);
  }

  @Override
  protected boolean isClientError(GraphQLError error) {
    if (error.getErrorType() == ErrorType.MutationNotSupported) {
      return false;
    }
    return (error.getErrorType() == ErrorType.InvalidSyntax);
  }

  @Override
  protected List<GraphQLError> filterGraphQLErrors(List<GraphQLError> errors) {
    return errors
        .stream()
        .filter(e -> e instanceof ExceptionWhileDataFetching ? true : isClientError(e))
        .map(
            e ->
                e instanceof ExceptionWhileDataFetching
                    ? new SanitizedError((ExceptionWhileDataFetching) e)
                    : e)
        .collect(Collectors.toList());
  }
}
