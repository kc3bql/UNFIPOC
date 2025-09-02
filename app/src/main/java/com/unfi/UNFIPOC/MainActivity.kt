// MainActivity.kt
package com.unfi.UNFIPOC

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random

// -------------------------
// Data Models
// -------------------------
data class Product(
    val id: Int,
    val name: String,
    val price: Double,
    val category: String,
    val description: String,
    val emoji: String,
    val stock: Int
)

data class CartItem(
    val product: Product,
    val quantity: Int
) {
    val subtotal: Double get() = product.price * quantity
}

// -------------------------
// Mock API Service
// -------------------------
class MockFoodApiService {
    companion object {
        private val mockProducts = listOf(
            Product(1, "Organic Bananas", 2.99, "Produce", "Per pound, organic yellow bananas", "🍌", 85),
            Product(2, "Roma Tomatoes", 3.49, "Produce", "Fresh roma tomatoes per pound", "🍅", 42),
            Product(3, "Organic Spinach", 4.99, "Produce", "5oz container fresh baby spinach", "🥬", 28),
            Product(4, "Red Bell Peppers", 5.99, "Produce", "Per pound, fresh red bell peppers", "🫑", 35),
            Product(5, "Avocados", 6.99, "Produce", "Package of 4 ripe avocados", "🥑", 63),
            Product(6, "Whole Milk", 4.49, "Dairy", "1 gallon whole milk", "🥛", 120),
            Product(7, "Large Eggs", 3.99, "Dairy", "Dozen large grade A eggs", "🥚", 95),
            Product(8, "Cheddar Cheese", 7.99, "Dairy", "8oz sharp cheddar cheese block", "🧀", 67),
            Product(9, "Greek Yogurt", 5.99, "Dairy", "32oz container plain Greek yogurt", "🥛", 45),
            Product(10, "Butter", 6.49, "Dairy", "1 pound salted butter", "🧈", 78),
            Product(11, "Ground Beef", 8.99, "Meat", "1 pound 85% lean ground beef", "🥩", 32),
            Product(12, "Chicken Breast", 12.99, "Meat", "Per pound boneless skinless", "🐔", 28),
            Product(13, "Salmon Fillet", 16.99, "Meat", "Per pound Atlantic salmon fillet", "🐟", 18),
            Product(14, "Whole Wheat Bread", 3.99, "Bakery", "24oz loaf whole wheat bread", "🍞", 55),
            Product(15, "Bagels", 4.99, "Bakery", "6-pack everything bagels", "🥯", 40),
            Product(16, "Pasta", 2.49, "Pantry", "1 pound box penne pasta", "🍝", 180),
            Product(17, "Rice", 4.99, "Pantry", "2 pound bag jasmine rice", "🍚", 150),
            Product(18, "Olive Oil", 8.99, "Pantry", "500ml extra virgin olive oil", "🫒", 75),
            Product(19, "Cereal", 5.99, "Pantry", "Family size honey nut cereal", "🥣", 90),
            Product(20, "Coffee", 12.99, "Beverages", "12oz bag ground coffee medium roast", "☕", 65)
        )
    }

    suspend fun getProducts(): List<Product> {
        delay(800)
        return mockProducts
    }

    suspend fun getCategories(): List<String> {
        delay(300)
        return mockProducts.map { it.category }.distinct()
    }

    suspend fun submitOrder(cartItems: List<CartItem>): Boolean {
        delay(1200)
        return Random.nextDouble() > 0.15
    }
}

// -------------------------
// Repository
// -------------------------
class FoodRepository {
    private val apiService = MockFoodApiService()
    suspend fun getProducts() = apiService.getProducts()
    suspend fun getCategories() = apiService.getCategories()
    suspend fun submitOrder(cartItems: List<CartItem>) = apiService.submitOrder(cartItems)
}

// -------------------------
// ViewModel
// -------------------------
class FoodPosViewModel : ViewModel() {
    companion object { private const val TAX_RATE = 0.08875 }

    private val repository = FoodRepository()

    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var categories by mutableStateOf<List<String>>(emptyList()); private set
    var cart by mutableStateOf<List<CartItem>>(emptyList()); private set
    var selectedCategory by mutableStateOf("All"); private set
    var isLoading by mutableStateOf(false); private set
    var orderMessage by mutableStateOf<String?>(null); private set
    var isCartExpanded by mutableStateOf(true); private set

    val filteredProducts: List<Product>
        get() = if (selectedCategory == "All") products else products.filter { it.category == selectedCategory }

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch {
            isLoading = true
            try {
                products = repository.getProducts()
                categories = listOf("All") + repository.getCategories()
            } catch (_: Exception) {
                orderMessage = "Error loading data. Check connection and retry."
            } finally { isLoading = false }
        }
    }

    fun selectCategory(category: String) { selectedCategory = category }
    fun toggleCart() { isCartExpanded = !isCartExpanded }

    fun getInCartQty(productId: Int): Int =
        cart.firstOrNull { it.product.id == productId }?.quantity ?: 0

    fun addToCart(product: Product) {
        val inCart = getInCartQty(product.id)
        if (inCart >= product.stock) return // guard: don't exceed stock
        cart = if (inCart > 0) {
            cart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            cart + CartItem(product, 1)
        }
    }

    fun updateCartQuantity(productId: Int, newQuantity: Int) {
        cart = if (newQuantity <= 0) {
            cart.filter { it.product.id != productId }
        } else {
            // hard guard against exceeding stock
            val stock = products.firstOrNull { it.id == productId }?.stock ?: Int.MAX_VALUE
            val clamped = newQuantity.coerceAtMost(stock)
            cart.map { if (it.product.id == productId) it.copy(quantity = clamped) else it }
        }
    }

    fun getCartTotal(): Double = cart.sumOf { it.subtotal }
    fun getCartTax(): Double = getCartTotal() * TAX_RATE
    fun getCartGrandTotal(): Double = getCartTotal() + getCartTax()

    fun submitOrder() {
        if (cart.isEmpty()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val success = repository.submitOrder(cart)
                orderMessage = if (success) {
                    cart = emptyList()
                    "Order submitted successfully!"
                } else "Failed to submit order. Please try again."
            } catch (e: Exception) {
                orderMessage = "Order failed: ${e.message ?: "Unknown error"}"
            } finally { isLoading = false }
        }
    }

    fun clearOrderMessage() { orderMessage = null }
}

// -------------------------
// UI Composables
// -------------------------
@Composable
fun ProductCard(
    product: Product,
    inCartQty: Int,
    onAddToCart: () -> Unit
) {
    val remaining = (product.stock - inCartQty).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .padding(2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top: Emoji
            Text(
                text = product.emoji,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Product Name
            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Price
            Text(
                text = NumberFormat.getCurrencyInstance().format(product.price),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(Modifier.weight(1f))

            // Bottom: Quantity info and Add button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Quantity display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cart: $inCartQty",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Stock: $remaining",
                        fontSize = 10.sp,
                        color = if (remaining > 0) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.error
                    )
                }

                // Add button
                Button(
                    onClick = onAddToCart,
                    enabled = remaining > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add to cart",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    cartItem: CartItem,
    onQuantityChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = cartItem.product.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    text = NumberFormat.getCurrencyInstance().format(cartItem.product.price),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onQuantityChange(cartItem.quantity - 1) },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("-", fontSize = 16.sp) }

                Text(
                    text = "${cartItem.quantity}",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Button(
                    onClick = { onQuantityChange(cartItem.quantity + 1) },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("+", fontSize = 16.sp) }
            }

            Text(
                text = NumberFormat.getCurrencyInstance().format(cartItem.subtotal),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPosApp(viewModel: FoodPosViewModel) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.orderMessage) {
        viewModel.orderMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOrderMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UNFI Grocery POS") },
                actions = {
                    TextButton(
                        onClick = { viewModel.toggleCart() },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🛒",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${viewModel.cart.size}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Categories
            if (viewModel.categories.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Categories:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            viewModel.categories.take(3).forEach { category ->
                                val selected = viewModel.selectedCategory == category
                                Button(
                                    onClick = { viewModel.selectCategory(category) },
                                    modifier = Modifier.weight(1f),
                                    colors = if (selected)
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    else ButtonDefaults.outlinedButtonColors(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) { Text(category, fontSize = 11.sp, maxLines = 1) }
                            }
                        }
                        if (viewModel.categories.size > 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val rest = viewModel.categories.drop(3)
                                rest.forEach { category ->
                                    val selected = viewModel.selectedCategory == category
                                    Button(
                                        onClick = { viewModel.selectCategory(category) },
                                        modifier = Modifier.weight(1f),
                                        colors = if (selected)
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        else ButtonDefaults.outlinedButtonColors(),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) { Text(category, fontSize = 11.sp, maxLines = 1) }
                                }
                                repeat((3 - rest.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            // Loading
            if (viewModel.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }

            // Products
            if (!viewModel.isLoading) {
                item {
                    Text(
                        text = "Products (${viewModel.filteredProducts.size} items)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val rows = viewModel.filteredProducts.chunked(3)
                items(rows) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { product ->
                            Box(Modifier.weight(1f)) {
                                ProductCard(
                                    product = product,
                                    inCartQty = viewModel.getInCartQty(product.id),
                                    onAddToCart = { viewModel.addToCart(product) }
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                // Cart (collapsible)
                if (viewModel.isCartExpanded) {
                    item {
                        Text(
                            text = "Cart (${viewModel.cart.size} items)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (viewModel.cart.isEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth().height(60.dp)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Your cart is empty", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    } else {
                        items(viewModel.cart) { cartItem ->
                            CartItemRow(
                                cartItem = cartItem,
                                onQuantityChange = { q -> viewModel.updateCartQuantity(cartItem.product.id, q) }
                            )
                        }

                        // Totals
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Subtotal:", fontSize = 12.sp)
                                        Text(currency.format(viewModel.getCartTotal()), fontSize = 12.sp)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Tax (8.875%):", fontSize = 12.sp)
                                        Text(currency.format(viewModel.getCartTax()), fontSize = 12.sp)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            currency.format(viewModel.getCartGrandTotal()),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Submit
                        item {
                            Button(
                                onClick = { viewModel.submitOrder() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                enabled = !viewModel.isLoading
                            ) {
                                if (viewModel.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Submit Order", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------
// Main Activity
// -------------------------
class MainActivity : ComponentActivity() {
    private val viewModel: FoodPosViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FoodPosApp(viewModel)
                }
            }
        }
    }
}