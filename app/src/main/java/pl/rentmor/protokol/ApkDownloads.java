package pl.rentmor.protokol;
/** Each user-initiated download asks the server for the currently published release. */
final class ApkDownloads {
    static String url(long requestedAt){return "https://rentmor.pl/download-app.php?request="+requestedAt;}
}
