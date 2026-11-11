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
