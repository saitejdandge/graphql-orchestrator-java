package helpers

import com.intuit.graphql.orchestrator.GraphQLOrchestrator
import com.intuit.graphql.orchestrator.ServiceProvider
import com.intuit.graphql.orchestrator.schema.Operation
import com.intuit.graphql.orchestrator.schema.RuntimeGraph
import com.intuit.graphql.orchestrator.stitching.SchemaStitcher
import com.intuit.graphql.orchestrator.testhelpers.MockServiceProvider
import com.intuit.graphql.orchestrator.testhelpers.SimpleMockServiceProvider
import graphql.ExecutionInput
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategy
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.parser.Parser
import spock.lang.Specification

class BaseIntegrationTestSpecification extends Specification {

    public static final Parser PARSER = new Parser()

    def testService

    def createSimpleMockService(String testSchema, Map<String, Object> mockServiceResponse) {
        return SimpleMockServiceProvider.builder()
                .sdlFiles(["schema.graphqls": testSchema])
                .mockResponse(mockServiceResponse)
                .build()
    }

    def createSimpleMockService(String namespace, String testSchema, Map<String, Object> mockServiceResponse) {
        return SimpleMockServiceProvider.builder()
                .sdlFiles(["schema.graphqls": testSchema])
                .namespace(namespace)
                .mockResponse(mockServiceResponse)
                .build()
    }

    def createSimpleMockService(String namespace, ServiceProvider.ServiceType serviceType,
                                String testSchema, Map<String, Object> mockServiceResponse) {
        return SimpleMockServiceProvider.builder()
                .sdlFiles(["schema.graphqls": testSchema])
                .namespace(namespace)
                .serviceType(serviceType)
                .mockResponse(mockServiceResponse)
                .build()
    }

    def createQueryMatchingService(String namespace, ServiceProvider.ServiceType serviceType,
                                   String testSchema, Map<String, Object> mockServiceResponse) {
        return MockServiceProvider.builder()
                .sdlFiles(["schema.graphqls": testSchema])
                .namespace(namespace)
                .serviceType(serviceType)
                .responseMap(mockServiceResponse)
                .build()
    }

    def createQueryMatchingService(String namespace, String testSchema, Map<String, Object> mockServiceResponse) {
        return this.createQueryMatchingService(namespace, ServiceProvider.ServiceType.GRAPHQL,
                testSchema, mockServiceResponse)
    }

    static GraphQLOrchestrator createGraphQLOrchestrator(ServiceProvider service) {
       return createGraphQLOrchestrator(Arrays.asList(service));
    }

    static GraphQLOrchestrator createGraphQLOrchestrator(ServiceProvider... services) {
        return createGraphQLOrchestrator(new AsyncExecutionStrategy(), new AsyncExecutionStrategy(),
                services);
    }

    static GraphQLOrchestrator createGraphQLOrchestrator(List<ServiceProvider> services) {
        return createGraphQLOrchestrator(new AsyncExecutionStrategy(), new AsyncExecutionStrategy(),
                (ServiceProvider[])services)
    }

    static GraphQLOrchestrator createGraphQLOrchestrator(
            ExecutionStrategy queryExecutionStrategy,
            ExecutionStrategy mutationExecutionStrategy,
            ServiceProvider... services) {

        RuntimeGraph runtimeGraph = SchemaStitcher.newBuilder()
                .services(Arrays.asList(services)).build().stitchGraph();

        GraphQLOrchestrator.Builder builder = GraphQLOrchestrator.newOrchestrator();

        builder.runtimeGraph(runtimeGraph);
        builder.instrumentations(Collections.emptyList());
        builder.executionIdProvider(ExecutionIdProvider.DEFAULT_EXECUTION_ID_PROVIDER);

        if (Objects.nonNull(queryExecutionStrategy)) {
            builder.queryExecutionStrategy(queryExecutionStrategy)
        }
        if (Objects.nonNull(mutationExecutionStrategy)) {
            builder.mutationExecutionStrategy(mutationExecutionStrategy)
        }

        return builder.build();
    }

    static ExecutionInput createExecutionInput(String graphqlQuery, Map<String, Object> variables) {
        ExecutionInput.newExecutionInput()
                .query(graphqlQuery)
                .variables(variables)
                .build()
    }

    static ExecutionInput createExecutionInput(String graphqlQuery) {
        createExecutionInput(graphqlQuery, Collections.emptyMap())
    }

    static boolean compareQueryToExecutionInput(Operation operation, String query,
                                                SimpleMockServiceProvider simpleMockServiceProvider) {
        if (query == null) {
            return getExecutionInputQueryNoSpace(simpleMockServiceProvider) == null
        }
        else {
            String graphQuery = (operation != null) ? operation.toString().toLowerCase() +
                                                      operation.toString().toUpperCase() + query
                                                    : query
            graphQuery = graphQuery.replaceAll("\\s", "");
            return (graphQuery) == getExecutionInputQueryNoSpace(simpleMockServiceProvider)
        }
    }

    static String getExecutionInputQueryNoSpace(SimpleMockServiceProvider simpleMockServiceProvider) {
        String query = getExecutionInputQuery(simpleMockServiceProvider)
        return query != null ? query.replaceAll("\\s", "") : null
    }

    static String getExecutionInputQuery(SimpleMockServiceProvider simpleMockServiceProvider) {
        try {
            Object executionInputArgs = simpleMockServiceProvider.executionInputArgumentCaptor.capturingMatcher.arguments
            return (executionInputArgs != null && !executionInputArgs.isEmpty())
                    ? executionInputArgs.get(0).query
                    : null;
        } catch (NullPointerException e) {
            return null;    // NPE hidden as its expected to be thrown
        }
    }

    static String checkIfKeyExistsInDataFetcherMap(GraphQLOrchestrator graphQLOrchestrator, String key){
        return graphQLOrchestrator.runtimeGraph.codeRegistry.dataFetcherMap.entrySet().stream().anyMatch(
                { entry ->
                    (String.format("%s.%s", entry.getKey().getTypeName(), entry.getKey().getFieldName().toString())
                            != key) })
    }

    ExecutionInput getCapturedDownstreamExecutionInput() {
        return testService.getExecutionInputArgumentCaptor().getValue()
    }

    ExecutionInput getCapturedDownstreamExecutionInput(SimpleMockServiceProvider simpleMockServiceProvider) {
        return simpleMockServiceProvider.getExecutionInputArgumentCaptor().getValue()
    }

    Object toDocument(String query) {
        return PARSER.parseDocument(query)
    }

    OperationDefinition getQueryOperationDefinition(Document document) {
        return document.getDefinitions().stream()
                .filter({ definition -> definition instanceof OperationDefinition })
                .map({ definition -> (OperationDefinition) definition })
                .filter({ operationDefinition -> operationDefinition.getOperation() == OperationDefinition.Operation.QUERY })
                .findFirst().get();

    }
}
