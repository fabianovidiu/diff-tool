# Diff Tool Coding Challenge

A single java file was created to simplify the delivery and execution: `DiffTool.java`.  
The file can be executed with Java 17 using: `java DiffTool.java` (no frameworks or additional dependencies have been used).

## The file contains:
- a `Main` class - used to execute and test the code
- a `DiffTool` class - containing a single method called `diff` (as requested)
- a `ChangeType` interface with its two implementations `PropertyUpdate` and `ListUpdate` (as requested)
- additional support code (eg. `AuditKey` annotation, some POJOs/records without any logic)

## Notes:
- the `DiffTool` class also contains a class variable called propertyName (requirements did not mention anything about class variables)
- lots of improvements can be done, if other private method are extracted, but that would violate the requirements, so I tried to keep everything in one method (not something I normally do)
- the whole thing (including this documentations) took around 4h (initially I built a solution without recursion, but I did not like how it looked)
