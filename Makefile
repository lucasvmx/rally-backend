COMPOSE ?= docker compose
COMPOSE_FILE ?= docker-compose.yml
ENV_FILE ?= ../.env
PROJECT_NAME ?= rally-backend

ENV_FILE_ARG = $(if $(wildcard $(ENV_FILE)),--env-file "$(ENV_FILE)")
DC = $(COMPOSE) --project-name $(PROJECT_NAME) $(ENV_FILE_ARG) -f "$(COMPOSE_FILE)"

.DEFAULT_GOAL := help
.PHONY: help build up deploy start stop restart down status logs

help:
	@printf '%s\n' \
		'Comandos disponíveis (GNU Make 4.x):' \
		'  make build    Gera a imagem do backend' \
		'  make up       Constrói e inicia o backend e o PostgreSQL' \
		'  make deploy   Alias de make up' \
		'  make status   Mostra o estado dos serviços' \
		'  make logs     Acompanha os logs dos serviços' \
		'  make restart  Reinicia o backend' \
		'  make stop     Para os serviços sem remover os contêineres' \
		'  make start    Inicia os contêineres parados' \
		'  make down     Remove os contêineres, preservando os dados do banco'

build:
	$(DC) build backend

up:
	$(DC) up --build --detach --remove-orphans --wait --wait-timeout 180

deploy: up

start:
	$(DC) start

stop:
	$(DC) stop

restart:
	$(DC) restart backend

down:
	$(DC) down

status:
	$(DC) ps

logs:
	$(DC) logs --follow --tail=100 backend banco
