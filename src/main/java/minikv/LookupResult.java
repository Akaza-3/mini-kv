package minikv;

public sealed interface LookupResult {
    record Found(String value) implements LookupResult {}
    record Deleted() implements LookupResult {}
    record Absent() implements LookupResult {}
}