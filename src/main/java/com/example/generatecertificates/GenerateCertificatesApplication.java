package com.example.generatecertificates;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class GenerateCertificatesApplication {

    private static String arg1, arg2, pathStr, dirCMD;
    public static List<String> commands = new ArrayList<String>();

    // Nombre de la carpeta
    private  static String folderName = "testCerts";
    static {
        OSPParameters(folderName);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        SpringApplication.run(GenerateCertificatesApplication.class, args);


        agregarComando("mkdir " + dirCMD + "\\tmp");
        agregarComando("mkdir " + dirCMD + "\\certs");
        agregarComando("mkdir " + dirCMD + "\\certs\\security_save");

        agregarComando("mkdir " + dirCMD + "\\crl");


        agregarComando("type nul > " + dirCMD + "\\index.txt");
        agregarComando("type nul > " + dirCMD + "\\serial");
        agregarComando("type nul > " + dirCMD + "\\crlnumber");
        agregarComando("echo 01 > " + dirCMD + "\\serial ");
        agregarComando("echo 1000 > " + dirCMD + "\\crlnumber");

        crearArchivosConfiguracion2();
        createTemplate("localhost",pathStr+"/certs");




        exec("openssl req -x509 -sha256 -days 3650 -newkey rsa:4096 -passout pass:123456 -keyout rootCA.key -out rootCA.crt -subj /CN=FAC/OU=CETAD/O=UCO/L=RIO/ST=ANT/C=CO",pathStr);

        exec("openssl ca -config openssl.cnf -gencrl -out ./crl/rootca.crl -passin pass:123456",pathStr);


        String[] commands= {"openssl req -new -newkey rsa:4096 -keyout localhost.key -passout pass:123456 -subj /CN=C3E/OU=CETAD/O=UCO/L=RIO/ST=ANT/C=CO",
                            "openssl req -new -key localhost.key -out server.csr -passin pass:123456 -subj /CN=localhost/OU=CETAD/O=UCO/L=RIO/ST=ANT/C=CO",
                            "openssl ca -config openssl.cnf -passin pass:123456 -notext -batch -in server.csr -out ./certs/certificate.crt -days 365 -extfile ./certs/ext_template.cnf -rand_serial",
                            "openssl pkcs12 -export -out ./certs/certificate.p12 -name \"localhost\" -inkey localhost.key -passin pass:123456 -passout pass:123456 -in ./certs/certificate.crt -certfile rootCA.crt ",
                            "keytool -importkeystore -srckeystore ./certs/certificate.p12 -srcstoretype PKCS12 -deststorepass 123456 -srcstorepass 123456 -destkeystore ./certs/certificate.jks -deststoretype JKS ",
                            "keytool -keystore ./certs/certificate.jks -alias CARoot -import -file rootCA.crt -storepass 123456 -noprompt "
        };

        try {
            for (String command : commands) {
                exec(command, pathStr);


            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }


    }


    public static void exec(String commandToExec, String actualFolder) throws IOException {

        RuntimeExecutor runtimeExecutor = new RuntimeExecutor();
        RuntimeExecutor.StreamReader exec = runtimeExecutor.exec(commandToExec, actualFolder);
        exec.addObserver((o, arg) -> {
        });

        int resultCode = exec.call();

        if (resultCode != 0) {
            throw new IllegalArgumentException(runtimeExecutor.getLog() + "\n" + runtimeExecutor.getErrLog());
        }


    }

    public static void OSPParameters(String folderName) {
        try {
            if (isWindows()) {
                arg1 = "cmd.exe";
                arg2 = "/c";
                pathStr = "./"+folderName;
                dirCMD = ".\\"+folderName;
            } else {
                arg1 = "bash";
                arg2 = "-c";
                pathStr = "tls";
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
    }

    public static void crearArchivosConfiguracion2() throws IOException {

        // Crear openssl.cnf

        String contentConfig = "[ ca ]\n" +
                "default_ca      = CA_default            # The default ca section\n" +
                "\n" +
                "[ CA_default ]\n" +
                "dir             =  .             # Where everything is kept\n" +
                "certs           = $dir/certs            # Where the issued certs are kept\n" +
                "crl_dir         = $dir/crl              # Where the issued crl are kept\n" +
                "database        = $dir/index.txt        # database index file.\n" +
                "new_certs_dir   = $dir/certs/security_save            # default place for new certs.\n" +
                "certificate     = $dir/rootCA.crt         # The CA certificate\n" +
                "serial          = $dir/serial           # The current serial number\n" +
                "crlnumber       = $dir/crlnumber        # the current crl number\n" +
                "crl             = $dir/rootca.crl          # The current CRL\n" +
                "private_key     = $dir/rootCA.key # The private key\n" +
                "x509_extensions = v3_ca                 # The extensions to add to the cert\n" +
                "name_opt        = ca_default            # Subject Name options\n" +
                "cert_opt        = ca_default            # Certificate field options\n" +
                "\n" +
                "# crlnumber must also be commented out to leave a V1 CRL.\n" +
                " crl_extensions = crl_ext\n" +
                "\n" +
                "default_days    = 365                   # how long to certify for\n" +
                "default_crl_days= 30                    # how long before next CRL\n" +
                "default_md      = sha256                # use SHA-256 by default\n" +
                "preserve        = no                    # keep passed DN ordering\n" +
                "\n" +
                "policy          = policy_match\n" +
                "\n" +
                "# For the CA policy\n" +
                "[ policy_match ]\n" +
                "countryName             = optional\n" +
                "stateOrProvinceName     = optional\n" +
                "organizationName        = optional\n" +
                "organizationalUnitName  = optional\n" +
                "commonName              = supplied\n" +
                "emailAddress            = optional\n" +
                "\n" +
                "####################################################################\n" +
                "[ req ]\n" +
                "default_bits            = 2048\n" +
                "default_md              = sha256\n" +
                "default_keyfile         = privkey.pem\n" +
                "distinguished_name      = req_distinguished_name\n" +
                "attributes              = req_attributes\n" +
                "x509_extensions = v3_ca # The extensions to add to the self signed cert\n" +
                "\n" +
                "[ req_distinguished_name ]\n" +
                "countryName                     = Country Name (2 letter code)\n" +
                "countryName_default             = IN\n" +
                "stateOrProvinceName             = State or Province Name (full name)\n" +
                "stateOrProvinceName_default     = Karnataka\n" +
                "localityName                    = Locality Name (eg, city)\n" +
                "localityName_default            = Bengaluru\n" +
                "0.organizationName              = Organization Name (eg, company)\n" +
                "0.organizationName_default      = GoLinuxCloud\n" +
                "organizationalUnitName          = Organizational Unit Name (eg, section)\n" +
                "organizationalUnitName_default  = Admin\n" +
                "\n" +
                "commonName                      = Common Name (eg, your name or your server\\'s hostname)\n" +
                "commonName_max                  = 64\n" +
                "\n" +
                "emailAddress                    = Email Address\n" +
                "emailAddress_max                = 64\n" +
                "\n" +
                "[ req_attributes ]\n" +
                "challengePassword               = A challenge password\n" +
                "challengePassword_min           = 4\n" +
                "challengePassword_max           = 20\n" +
                "unstructuredName                = An optional company name\n" +
                "\n" +
                "[ v3_req ]\n" +
                "# Extensions to add to a certificate request\n" +
                "basicConstraints = CA:FALSE\n" +
                "keyUsage = nonRepudiation, digitalSignature, keyEncipherment\n" +
                "\n" +
                "[ v3_ca ]\n" +
                "# Extensions for a typical CA\n" +
                "# PKIX recommendation.\n" +
                "subjectKeyIdentifier=hash\n" +
                "authorityKeyIdentifier=keyid:always,issuer\n" +
                "basicConstraints = critical,CA:true\n" +
                "subjectAltName    = @alt_names\n"+
                "\n" +
                "[ crl_ext ]\n" +
                "# CRL extensions.\n" +
                "# Only issuerAltName and authorityKeyIdentifier make any sense in a CRL.\n" +
                "authorityKeyIdentifier=keyid:always\n"+
                "\n"+
                "[alt_names]\n"+
                "DNS.1 = 192.168.251.201\n"+
                "DNS.2 = 127.0.0.1\n"+
                "DNS.3 = localhost\n"+
                "IP.1  = 127.0.0.1\n"+
                "IP.2 = 192.168.251.201";



        escribirArchivo(contentConfig, pathStr + "/openssl.cnf");


    }

    public static void escribirArchivo(String contenido, String ruta) throws IOException {

        try {
            // Create new file
            File file = new File(ruta);

            // If file doesn't exist, then create it

            file.createNewFile();

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            // Write in file
            bw.write(contenido);
            // Close connection
            bw.close();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static void agregarComando(String comando) throws IOException, InterruptedException {

        System.out.println(comando);
        commands.add(arg1);
        commands.add(arg2);
        commands.add(comando);
        ejecutar();

    }

    public static void createTemplate(String url, String ruta) throws IOException {
        // Crear ext_template.cnf

        String contentExtTemplate = MessageFormat.format(
                "authorityKeyIdentifier=keyid,issuer\n" +
                        "basicConstraints=CA:FALSE\n" +
                        "subjectAltName = @alt_names\n" +
                        "[alt_names]\n" +
                        "DNS.1 = localhost\n"+
                        "DNS.2 = {0}",url);

        escribirArchivo(contentExtTemplate, ruta+"/ext_template.cnf");


    }

    public static void ejecutar() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(commands);
        // Exp ###########################
        //builder.redirectErrorStream(true);
        // ###############################
        Process process = builder.start();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exitCode = process.waitFor();
        System.out.println("\nExited with error code : " + exitCode);

        commands.clear();

    }
}


