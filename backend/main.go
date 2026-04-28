package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

type Message struct {
	Status string `json:"status"`
	Content string `json:"content"`
}

func main() {
	http.HandleFunc("/hello", func(w http.ResponseWriter, r *http.Request) {
		res := Message{Status: "Success", Content: "Hello, Go API!"}
		//送信するデータをjsonに指定
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(res)
	})

	fmt.Println("Server starting at :8080...")
	http.ListenAndServe(":8080", nil)
}