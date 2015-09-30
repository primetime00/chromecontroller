@echo off
for %%A in (*.proto) do (
	protoc --java_out=..\android\app\src\main\java --proto_path=.\ %%A
)