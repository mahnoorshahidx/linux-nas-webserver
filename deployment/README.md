# Ubuntu Server Setup

## Software
Ubuntu with Java 21 JDK, Apache, MySQL, and curl.

Install with:
    sudo apt update
    sudo apt install -y openjdk-21-jdk apache2 mysql-server curl

## Start Spring Boot
From the repository root:
    cd nas-backend
    bash ./mvnw spring-boot:run

Keep that terminal open. The app runs on port 8080.
The current development login uses username user and the generated
password printed in the terminal during startup.

## Apache configuration
From the repository root in a second terminal:
    sudo a2enmod proxy proxy_http
    sudo cp deployment/apache/nas.conf /etc/apache2/sites-available/nas.conf
    sudo a2dissite 000-default.conf
    sudo a2ensite nas.conf
    sudo apache2ctl configtest && sudo systemctl reload apache2

Apache forwards requests from port 80 to Spring Boot on port 8080.

## VirtualBox forwarding
Network mode: NAT
Protocol: TCP
Host IP: 127.0.0.1
Host port: 8081
Guest IP: blank
Guest port: 80

Windows browser address:
http://127.0.0.1:8081/files.html

## Firewall
Run inside the Ubuntu VM console:
    sudo ufw default deny incoming
    sudo ufw default allow outgoing
    sudo ufw allow 80/tcp
    sudo ufw enable

## Verified
Java 21 is installed.
Apache and MySQL are active.
The file-management page loads from Windows through Apache.
The page loads with the firewall enabled.

Router forwarding and external remote access still need testing.

## Local HTTPS testing

The HTTPS site configuration is deployment/apache/nas-ssl.conf.
It uses a self-signed certificate for localhost, valid for 30 days.
This certificate is for local testing, not the final public deployment.

Create the certificate and private key on each server:
    sudo install -d -m 700 /etc/apache2/ssl
    sudo openssl req -x509 -newkey rsa:2048 -sha256 -noenc -days 30 -keyout /etc/apache2/ssl/nas-local.key -out /etc/apache2/ssl/nas-local.crt -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
    sudo chmod 600 /etc/apache2/ssl/nas-local.key

Enable the HTTPS site from the repository root:
    sudo a2enmod ssl headers
    sudo cp deployment/apache/nas-ssl.conf /etc/apache2/sites-available/nas-ssl.conf
    sudo a2ensite nas-ssl.conf
    sudo apache2ctl configtest && sudo systemctl reload apache2

Start Spring Boot with HTTPS proxy awareness and secure session cookies:
    cd nas-backend
    SERVER_FORWARD_HEADERS_STRATEGY=native SERVER_SERVLET_SESSION_COOKIE_SECURE=true bash ./mvnw spring-boot:run

These environment settings apply only to this run.
Use HTTPS when running with secure session cookies.

Ubuntu browser addresses:
    https://localhost/dashboard.html
    https://localhost/monitoring.html

The browser will warn because the local certificate is self-signed.
Verify HTTPS from an Ubuntu terminal:
    sudo curl --cacert /etc/apache2/ssl/nas-local.crt -I https://localhost/dashboard.html

An unauthenticated request can return 401. The session cookie should
include Secure and HttpOnly.

Private keys and generated backups must not be committed.
Windows HTTPS forwarding has not been configured yet.
Public access and integration with the team's final authentication
and permissions remain pending.
