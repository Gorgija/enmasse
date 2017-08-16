#!/bin/bash

# This script is for deploying EnMasse into Kubernetes. The target of
# installation can be an existing Kubernetes deployment or an all-in-one
# container can be started.
#
# In either case, access to the `kubectl` command is required.
#
# example usage:
#
#    $ deploy-kubernetes.sh -c 10.0.1.100 -l
#
# this will deploy EnMasse into the Kubernetes master running at 10.0.1.100
# and apply the external load balancer support for Azure, AWS etc.  For further
# parameters please see the help text.

if which kubectl &> /dev/null
then :
else
    echo "Cannot find kubectl command, please check path to ensure it is installed"
    exit 1
fi

SCRIPTDIR=`dirname $0`
TEMPLATE_PARAMS=""
ENMASSE_TEMPLATE=$SCRIPTDIR/kubernetes/enmasse.yaml
KEYCLOAK_TEMPLATE=$SCRIPTDIR/kubernetes/addons/keycloak.yaml
KEYCLOAK_TEMPLATE_PARAMS=""
KEYCLOAK_PASSWORD=`head /dev/urandom | tr -dc A-Za-z0-9 | head -c 128`
DEFAULT_NAMESPACE=enmasse
GUIDE=false

while getopts dgk:lm:n:t:vh opt; do
    case $opt in
        d)
            ALLINONE=true
            ;;
        g)
            GUIDE=true
            ;;
        k)
            KEYCLOAK_PASSWORD=$OPTARG
            ;;
        l)
            EXTERNAL_LB=true
            ;;
        m)
            MASTER_URI=$OPTARG
            ;;
        n)
            NAMESPACE=$OPTARG
            ;;
        t)
            ALT_TEMPLATE=$OPTARG
            ;;
        v)
            set -x
            ;;
        h)
            echo "usage: deploy-kubernetes.sh [options]"
            echo
            echo "deploy the EnMasse suite into a running Kubernetes cluster"
            echo
            echo "optional arguments:"
            echo "  -h                   show this help message"
            echo "  -d                   create an all-in-one minikube VM on localhost"
            echo "  -k KEYCLOAK_PASSWORD The password to use as the keycloak admin user"
            echo "  -m MASTER            Kubernetes master URI to login against (default: https://localhost:8443)"
            echo "  -n NAMESPACE         Namespace to deploy EnMasse into (default: $DEFAULT_NAMESPACE)"
            echo "  -o HOSTNAME          Custom hostname for messaging endpoint (default: use autogenerated from template)"
            echo "  -t TEMPLATE          An alternative Kubernetes template file to deploy EnMasse"
            echo
            exit
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            exit
            ;;
    esac
done

source $SCRIPTDIR/common.sh
TEMPDIR=`tempdir`

if [ -z "$NAMESPACE" ]
then
    NAMESPACE=$DEFAULT_NAMESPACE
fi

e=`kubectl get namespace ${NAMESPACE} 2> /dev/null`
if [ $? -gt 0 ]; then
    runcmd "kubectl create namespace $NAMESPACE" "Create namespace $NAMESPACE"
fi

if [ -n "$ALLINONE" ]
then
    if [ -n "$MASTER_URI" ]
    then
        echo "Error: You have requested an all-in-one deployment AND specified a cluster address."
        echo "Please choose one of these options and restart."
        exit 1
    fi
    runcmd "minikube start" "Start local minikube cluster"
    runcmd "minikube addons enable ingress" "Enabling ingress controller"
fi

runcmd "kubectl create sa enmasse-service-account -n $NAMESPACE" "Create service account for address controller"

SERVER_KEY=${TEMPDIR}/enmasse-controller.key
SERVER_CERT=${TEMPDIR}/enmasse-controller.crt
runcmd "openssl req -new -x509 -batch -nodes -out ${SERVER_CERT} -keyout ${SERVER_KEY}" "Create self-signed certificate"

runcmd "cat <<EOF | kubectl create -n ${NAMESPACE} -f -
{
    \"apiVersion\": \"v1\",
    \"kind\": \"Secret\",
    \"metadata\": {
        \"name\": \"address-controller-certs\"
    },
    \"type\": \"kubernetes.io/tls\",
    \"data\": {
        \"tls.key\": \"\$(base64 -w 0 ${SERVER_KEY})\",
        \"tls.crt\": \"\$(base64 -w 0 ${SERVER_CERT})\"
    }
}
EOF" "Create secret for controller certificate"

runcmd "kubectl create secret generic keycloak-credentials --from-literal=admin.key=$KEYCLOAK_PASSWORD" "Create secret with keycloak admin password"

if [ -n "$ALT_TEMPLATE" ]
then
    ENMASSE_TEMPLATE=$ALT_TEMPLATE
fi

runcmd "kubectl apply -f $KEYCLOAK_TEMPLATE -n $NAMESPACE" "Deploy Keycloak to $NAMESPACE"
runcmd "kubectl apply -f $ENMASSE_TEMPLATE -n $NAMESPACE" "Deploy EnMasse to $NAMESPACE"

if [ "$EXTERNAL_LB" == "true" ]
then
    runcmd "kubectl apply -f kubernetes/addons/external-lb.yaml"
fi
