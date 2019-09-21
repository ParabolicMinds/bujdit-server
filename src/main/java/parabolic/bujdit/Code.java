package parabolic.bujdit;

public enum Code {

    // GENERAL
    Success(0),
    ServerException(1),
    CommandNotFound(2),
    MissingRequiredField(3),

    // USER
    UsernameOrPasswordInvalid(100),
    ExpiredOrInvalidSession(101),
    CommandRequiresAuthentication(102),

    // BUJDIT
    BujditNotFound(200),
    ;

    public final int code;
    Code(int v) { this.code = v; }
}
