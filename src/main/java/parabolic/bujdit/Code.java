package parabolic.bujdit;

public enum Code {

    // GENERAL
    Success(0),
    ServerException(1),
    CommandNotFound(2),
    MissingRequiredField(3),
    InvalidFieldFormat(4),

    // USER ACCESS
    UsernameOrPasswordInvalid(100),
    ExpiredOrInvalidSession(101),
    CommandRequiresAuthentication(102),
    NotFoundOrInsufficientPermissions(103),
    ;

    public final int code;
    Code(int v) { this.code = v; }
}
