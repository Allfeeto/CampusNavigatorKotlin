package com.example.campusnavigator.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusnavigator.R
import com.example.campusnavigator.api.ApiService
import com.example.campusnavigator.api.models.Endpoint
import com.example.campusnavigator.api.models.Node
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import retrofit2.converter.scalars.ScalarsConverterFactory

class RouteFragment : Fragment() {

    private lateinit var startAutoComplete: MaterialAutoCompleteTextView
    private lateinit var endAutoComplete: MaterialAutoCompleteTextView
    private lateinit var findRouteButton: Button
    private lateinit var webView: WebView
    private lateinit var floorRecyclerView: RecyclerView
    private lateinit var apiService: ApiService
    private var currentFloor: Int = 1
    private var floors: List<Int> = emptyList()
    private var currentRoute: List<Node> = emptyList()
    private var allEndpoints: List<Endpoint> = emptyList()
    private val floorAdapter = FloorAdapter { floor ->
        loadFloorSvg(floor)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("RouteFragment", "onCreateView called")
        val view = inflater.inflate(R.layout.fragment_route, container, false)

        startAutoComplete = view.findViewById(R.id.start_autocomplete)
        endAutoComplete = view.findViewById(R.id.end_autocomplete)




        findRouteButton = view.findViewById(R.id.find_route_button)
        webView = view.findViewById(R.id.webview)
        floorRecyclerView = view.findViewById(R.id.floor_recycler_view)

        // Настройка RecyclerView
        floorRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        floorRecyclerView.adapter = floorAdapter

        // Инициализация Retrofit
        val retrofit = Retrofit.Builder()
            // .baseUrl("http://10.0.2.2:8000/")
            .baseUrl("https://se-context-utilization-projects.trycloudflare.com//")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        // Настройка WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.setInitialScale(100); // Устанавливаем масштаб 1:1 (100%)
        webView.settings.builtInZoomControls = false; // Отключаем встроенное масштабирование
        webView.settings.displayZoomControls = false; // Отключаем элементы управления масштабом
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("WebViewConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                return true
            }
        }
        webView.addJavascriptInterface(WebAppInterface(requireContext(), this), "android")

        view.post {
            webView.loadUrl("file:///android_asset/route.html")
            Log.d("RouteFragment", "WebView initialized and loaded route.html")
            // Инициализация этажей
            webView.evaluateJavascript("setFloors([1, 2])", null)
            // Загрузка первого этажа по умолчанию
            loadFloorSvg(1)
        }

        // Загрузка этажей
        loadFloors()

        // Настройка автодополнения
        setupAutoComplete()

        // Обработчик кнопки поиска маршрута
        findRouteButton.setOnClickListener {
            findRoute()
        }

        return view
    }







    private fun loadFloors() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val floors = apiService.getFloors()
                launch(Dispatchers.Main) {
                    this@RouteFragment.floors = floors
                    floorAdapter.submitList(floors)
                    Log.d("RouteFragment", "Floors loaded: $floors")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка загрузки этажей: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("RouteFragment", "Error loading floors: $e")
                if (e is retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
                    Log.e("RouteFragment", "HTTP ${e.code()} error body: $errorBody")
                }
            }
        }
    }

    fun loadFloorSvg(floorNumber: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getFloorSvg(floorNumber)
                Log.d("RouteFragment", "SVG response code: ${response.code()}")
                Log.d("RouteFragment", "SVG response headers: ${response.headers()}")
                if (response.isSuccessful) {
                    val svgContent = response.body() ?: ""
                    Log.d("RouteFragment", "SVG content preview: ${svgContent.take(100)}")
                    if (svgContent.isNotEmpty() && (svgContent.startsWith("<svg") || svgContent.startsWith("<?xml"))) {
                        launch(Dispatchers.Main) {
                            val escapedSvgContent = svgContent.replace("`", "\\`").replace("\${", "\\\${")
                            webView.evaluateJavascript("setSvgContent($floorNumber, `$escapedSvgContent`)", null)
                            currentFloor = floorNumber
                            Log.d("RouteFragment", "SVG loaded for floor $floorNumber, length: ${svgContent.length}")
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "SVG для этажа $floorNumber пустой или неверный", Toast.LENGTH_SHORT).show()
                        }
                        Log.e("RouteFragment", "Invalid SVG content for floor $floorNumber: $svgContent")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка загрузки SVG для этажа $floorNumber: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("RouteFragment", "Failed to load SVG for floor $floorNumber: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("RouteFragment", "Error loading SVG: $e")
            }
        }
    }

    private fun setupAutoComplete() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Получаем все точки
                allEndpoints = apiService.getEndpoints("")
                val sortedLabels = allEndpoints.map { it.label }.sorted()
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    sortedLabels
                )
                launch(Dispatchers.Main) {
                    startAutoComplete.setAdapter(adapter)
                    endAutoComplete.setAdapter(adapter)

                    startAutoComplete.setOnItemClickListener { parent, _, position, _ ->
                        val selectedLabel = parent.getItemAtPosition(position) as String
                        val selectedEndpoint = allEndpoints.find { it.label == selectedLabel }
                        if (selectedEndpoint != null) {
                            startAutoComplete.tag = selectedEndpoint.node_id
                            startAutoComplete.setText(selectedEndpoint.label, false)
                            Log.d("RouteFragment", "Start selected: ${selectedEndpoint.label} (node_id: ${selectedEndpoint.node_id})")
                        }
                    }

                    endAutoComplete.setOnItemClickListener { parent, _, position, _ ->
                        val selectedLabel = parent.getItemAtPosition(position) as String
                        val selectedEndpoint = allEndpoints.find { it.label == selectedLabel }
                        if (selectedEndpoint != null) {
                            endAutoComplete.tag = selectedEndpoint.node_id
                            endAutoComplete.setText(selectedEndpoint.label, false)
                            Log.d("RouteFragment", "End selected: ${selectedEndpoint.label} (node_id: ${selectedEndpoint.node_id})")
                        }
                    }

                    // Показываем выпадающий список при фокусе
                    startAutoComplete.setOnClickListener { startAutoComplete.showDropDown() }
                    endAutoComplete.setOnClickListener { endAutoComplete.showDropDown() }

                    // Фильтрация при вводе текста
                    startAutoComplete.setOnTouchListener { _, _ ->
                        startAutoComplete.showDropDown()
                        false
                    }
                    endAutoComplete.setOnTouchListener { _, _ ->
                        endAutoComplete.showDropDown()
                        false
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка загрузки точек: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("RouteFragment", "Error setting up autocomplete: $e")
            }
        }
    }

    private fun findRoute() {
        val startNodeId = startAutoComplete.tag as? Int
        val endNodeId = endAutoComplete.tag as? Int

        if (startNodeId == null || endNodeId == null) {
            Toast.makeText(context, "Выберите начальную и конечную точки", Toast.LENGTH_SHORT).show()
            Log.w("RouteFragment", "Invalid start or end node ID")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.findRoute(startNodeId, endNodeId)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val routeData = responseBody?.get("route") as? List<Map<String, Any>>
                    if (routeData != null) {
                        val route = routeData.map { map ->
                            Node(
                                id = (map["id"] as Double).toInt(),
                                name = map["name"] as String,
                                floor = (map["floor"] as Double).toInt(),
                                x = (map["x"] as Double).toFloat(),
                                y = (map["y"] as Double).toFloat()
                            )
                        }
                        launch(Dispatchers.Main) {
                            currentRoute = route
                            val routeJson = Gson().toJson(route)
                            webView.evaluateJavascript("displayRoute(`$routeJson`)", null)
                            if (route.isNotEmpty()) {
                                loadFloorSvg(route[0].floor)
                            }
                            Log.d("RouteFragment", "Route displayed with ${route.size} nodes: $routeJson")
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Маршрут не найден", Toast.LENGTH_SHORT).show()
                        }
                        Log.e("RouteFragment", "Route data is null: $responseBody")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка поиска маршрута: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("RouteFragment", "Failed to find route: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("RouteFragment", "Error finding route: $e")
            }
        }
    }

    fun clearRoute() {
        currentRoute = emptyList()
        webView.evaluateJavascript("displayRoute('[]')", null)
        loadFloorSvg(1) // Возвращаем на первый этаж
        startAutoComplete.setText("")
        startAutoComplete.tag = null
        endAutoComplete.setText("")
        endAutoComplete.tag = null
        Log.d("RouteFragment", "Route cleared")
    }
}

class FloorAdapter(private val onFloorSelected: (Int) -> Unit) :
    ListAdapter<Int, FloorAdapter.FloorViewHolder>(FloorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FloorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.floor_item, parent, false)
        return FloorViewHolder(view)
    }

    override fun onBindViewHolder(holder: FloorViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FloorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val floorButton: Button = itemView.findViewById(R.id.floor_button)

        fun bind(floor: Int) {
            floorButton.text = floor.toString()
            floorButton.setOnClickListener { onFloorSelected(floor) }
        }
    }

    class FloorDiffCallback : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean = oldItem == newItem
    }
}